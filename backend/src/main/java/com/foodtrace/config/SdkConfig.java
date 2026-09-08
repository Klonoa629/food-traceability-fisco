package com.foodtrace.config;

import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.config.ConfigOption;
import org.fisco.bcos.sdk.v3.config.exceptions.ConfigException;
import org.fisco.bcos.sdk.v3.config.model.ConfigProperty;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

/**
 * FISCO BCOS SDK 装配配置
 *
 * <p>读取证书路径、节点地址、群组 ID 等配置，初始化 {@link BcosSDK}
 * 并创建目标群组的 {@link Client}。
 */
@Configuration
public class SdkConfig {

    @Value("${fisco.cert-path}")
    private String certPath;

    @Value("${fisco.group-id}")
    private String groupId;

    @Value("${fisco.peers}")
    private String peers;

    /**
     * 初始化 SDK 实例：加载证书并建立与节点的长连接
     *
     * @return 全局唯一的 BcosSDK 实例
     */
    @Bean
    public BcosSDK bcosSDK() {
        ConfigProperty prop =  new ConfigProperty();
        prop.setNetwork(Map.of(
                "peers", List.of(peers),
                "defaultGroup", groupId));
        prop.setCryptoMaterial((Map.of("certPath", certPath)));
        prop.setThreadPool(Map.of("threadPoolSize", "16"));
        try{
            return new BcosSDK(new ConfigOption(prop));
        }catch (ConfigException e) {
            throw new IllegalStateException("FISCO SDK init failed", e);
        }
    }

    /**
     * 获取目标群组的链上操作客户端
     *
     * @param bcosSDK 已初始化的 SDK 实例
     * @return 绑定配置群组的 Client
     */
    @Bean
    public Client client(BcosSDK bcosSDK) {
        return bcosSDK.getClient(groupId);
    }
}

