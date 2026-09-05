package com.foodtrace.config;

import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.config.ConfigOption;
import org.fisco.bcos.sdk.v3.config.exceptions.ConfigException;
import org.fisco.bcos.sdk.v3.config.model.ConfigProperty;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

/* Spring配置类：读取证书路径、节点地址、群组ID等配置，初始化FISCO BCOS SDK实例并创建对应群组的链上操作客户端 */
@Configuration
public class SdkConfig {

    @Value("${fisco.cert-path}")
    private String certPath;

    @Value("${fisco.group-id}")
    private String groupId;

    @Value("${fisco.peers}")
    private String peers;

    // 初始化SDK，加载证书，连接节点
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

    // 获取群组客户端
    @Bean
    public Client client(BcosSDK bcosSDK) {
        return bcosSDK.getClient(groupId);
    }
}

