package com.foodtrace.chain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * WeBASE-Sign HTTP 客户端
 *
 * <p>封装对签名服务的 REST 调用，用托管私钥对交易哈希签名并返回签名结果。
 */
@Component
public class SignClient {
    private final RestClient rest = RestClient.create();

    @Value("${webase.sign-url}")
    private String signUrl;

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
                .uri(signUrl + "/sign/hash")
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
}
