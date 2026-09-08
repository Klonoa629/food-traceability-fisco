package com.foodtrace.chain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 *合约 ABI 资源持有者
 *
 * <p>启动时从 classpath 加载 Foodtrace.abi，缺失即启动失败。
 *
 * @author Microft0629
 * @since 2026-09-08
 */
@Component
public class AbiHolder {
    /** Foodtrace 合约 ABI */
    private final String foodtraceAbi;

    /**
     * 读取 classpath 下的 ABI 文件
     *
     * @param resource 由 Spring 注入的 classpath:abi/Foodtrace.abi 资源
     * @throws IOException 文件读取失败时抛出
     */
    public AbiHolder(@Value("classpath:abi/Foodtrace.abi") Resource resource) throws IOException {
        this.foodtraceAbi = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 获取 Foodtrace 合约 ABI
     *
     * @return ABI JSON 字符串
     */
    public String get() {
        return foodtraceAbi;
    }
}
