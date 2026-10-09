package com.foodtrace.chain;

import com.foodtrace.config.WebaseProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;

/**
 * WeBASE-Sign HTTP 客户端
 *
 * <p>封装对签名服务的 REST 调用：托管用户的开户/查询，
 * 以及用托管私钥对交易哈希签名并返回签名结果。
 *
 * @author Microft0629
 * @since 2026-09-17
 */
@Component
public class SignClient {
    /** Sign 已存在该托管用户的错误码 */
    private static final int CODE_USER_EXISTS = 303001;

    private final RestClient rest = RestClient.create();
    private final WebaseProperties properties;

    /**
     * 组装签名服务客户端
     *
     * @param properties WeBASE-Sign 连接配置
     */
    public SignClient(WebaseProperties properties) {
        this.properties = properties;
    }

    /**
     * 请求 Sign 用托管私钥对交易哈希签名
     *
     * @param signUserId  托管用户标识
     * @param messageHash 交易哈希（0x 前缀十六进制）
     * @return 130 位十六进制签名串（v || r || s，无 0x 前缀）
     * @throws IllegalStateException Sign 返回非 0 状态或签名长度异常时抛出
     */
    @SuppressWarnings("unchecked")
    public String signMessageHash(String signUserId, String messageHash) {
        Map<String, Object> resp = rest.post()
                .uri(properties.getSignUrl() + "/sign/hash")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("signUserId", signUserId, "messageHash", messageHash))
                .retrieve()
                .body(Map.class);
        if (resp == null || !Integer.valueOf(0).equals(resp.get("code"))) {
            throw new IllegalStateException("Webase-Sign 签名失败：" + resp);
        }
        Map<?, ?> data = (Map<?, ?>) resp.get("data");
        // 签名位于 data.signDataStr 字段
        String sig = (String) data.get("signDataStr");
        if (sig == null || sig.length() != 130) {
            throw new IllegalStateException("签名数据长度异常（期望130）" + sig);
        }
        return sig;
    }

    /**
     * 在 Sign 侧开立托管用户（ECDSA），已存在则直接查询返回
     *
     * @param signUserId 托管用户标识（数字字母下划线，不超过 64 位）
     * @return 含链上地址的托管用户信息
     * @throws IllegalStateException Sign 返回非 0 状态时抛出
     */
    @SuppressWarnings("unchecked")
    public SignUser createOrGet(String signUserId) {
        Map<String, Object> resp = rest.get()
                .uri(properties.getSignUrl() + "/user/newUser?signUserId={id}&appId={app}&encryptType=0",
                        signUserId, properties.getAppId())
                .retrieve()
                .body(Map.class);
        // 幂等处理：上次开户成功但落库失败时，直接取回已有托管用户
        if (resp != null && Integer.valueOf(CODE_USER_EXISTS).equals(resp.get("code"))) {
            return getUser(signUserId);
        }
        return parseUser(resp, "Webase-Sign 开户失败");
    }

    /**
     * 查询 Sign 侧托管用户信息
     *
     * @param signUserId 托管用户标识
     * @return 含链上地址的托管用户信息
     * @throws IllegalStateException 用户不存在或 Sign 返回非 0 状态时抛出
     */
    @SuppressWarnings("unchecked")
    public SignUser getUser(String signUserId) {
        Map<String, Object> resp = rest.get()
                .uri(properties.getSignUrl() + "/user/{id}/userInfo", signUserId)
                .retrieve()
                .body(Map.class);
        return parseUser(resp, "WeBASE-Sign 查询托管用户失败");
    }

    /**
     * 探测签名服务连通性
     *
     * <p>收到任何 HTTP 响应（含 4xx/5xx）即视为进程存活，
     * 仅连接失败判定为不可达。
     *
     * @return 可达返回 true
     */
    public boolean ping() {
        try {
            rest.get().uri(properties.getSignUrl() + "/user/ping/userInfo")
                    .retrieve().toBodilessEntity();
            return true;
        } catch (HttpStatusCodeException responded) {
            // 服务有响应即存活
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /**
     * 解析 Sign 用户接口响应
     *
     * @param resp   响应体
     * @param action 失败时的提示前缀
     * @return 托管用户信息
     * @throws IllegalStateException 状态非 0 或缺少地址时抛出
     */
    @SuppressWarnings("unchecked")
    private SignUser parseUser(Map<String, Object> resp, String action) {
        if (resp == null || !Integer.valueOf(0).equals(resp.get("code"))) {
            throw new IllegalStateException(action + "：" + resp);
        }
        Map<?, ?> data = (Map<?, ?>) resp.get("data");
        if (data == null || data.get("address") == null) {
            throw new IllegalStateException(action + "：响应缺少 address 字段");
        }
        return new SignUser((String) data.get("signUserId"), (String) data.get("address"));
    }

    /**
     * Sign 侧托管用户信息
     *
     * @param signUserId 托管用户标识
     * @param address    链上账户地址
     */
    public record SignUser(String signUserId, String address) {
    }
}
