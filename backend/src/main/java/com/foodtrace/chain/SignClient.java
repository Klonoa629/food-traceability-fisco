package com.foodtrace.chain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

/* HTTP客户端封装：负责请求WeBASE-Sign服务，用托管私钥给交易哈希签名并返回签名结果 */
@Component
public class SignClient {
    private final RestClient rest = RestClient.create();

    @Value("${webase.sign-url}")
    private String signUrl;

    // 获取Sign用私钥对交易哈希的签名
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
        String data = (String) resp.get("data");
        // ECDSA生成65字节，返回130位hex（不带“0x”）
        if (data == null || data.length() != 130) {
            throw new IllegalStateException("签名数据长度异常（期望130）" + data);
        }
        return data;
    }
}
