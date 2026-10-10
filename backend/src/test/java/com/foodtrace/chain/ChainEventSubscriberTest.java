package com.foodtrace.chain;

import com.foodtrace.config.ContractProperties;
import com.foodtrace.service.OperateLogService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.EventLog;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 合约事件订阅测试：内外部交易区分、缓存失效与外部审计
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@ExtendWith(MockitoExtension.class)
class ChainEventSubscriberTest {
    @Mock
    private Client client;
    @Mock
    private ProductCache productCache;
    @Mock
    private OperateLogService operateLogService;
    @Mock
    private ChainWriter chainWriter;

    private ChainEventSubscriber subscriber;

    /**
     * 构造被测订阅器
     */
    @BeforeEach
    void setUp() {
        ContractProperties properties = new ContractProperties();
        properties.setContractAddress("0x2af6160e266f763652f433a80b94fc13f4065303");
        subscriber = new ChainEventSubscriber(client, properties, productCache,
                operateLogService, chainWriter, new SimpleMeterRegistry());
    }

    /**
     * 构造事件主题（事件签名 keccak）
     */
    private static String topic(String signature) {
        CryptoSuite suite = new CryptoSuite(CryptoType.ECDSA_TYPE);
        return Hex.toHexStringWithPrefix(
                suite.hash(signature.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 构造产品 id 的 32 字节主题
     */
    private static String padded(long productId) {
        return "0x" + String.format("%064x", productId);
    }

    /**
     * 构造一条事件日志
     */
    private static EventLog log(String txHash, String... topics) {
        EventLog eventLog = new EventLog();
        eventLog.setTransactionHash(txHash);
        eventLog.setTopics(List.of(topics));
        return eventLog;
    }

    @Test
    void ownTransactionShouldEvictCacheWithoutAudit() {
        when(chainWriter.isOwn("0xown")).thenReturn(true);

        subscriber.handleLog(log("0xown", topic("RecordAdded(uint256,uint8,address)"), padded(5)));

        verify(productCache).evict(5L);
        verify(operateLogService, never()).record(anyLong(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    void externalProductEventShouldEvictAndAudit() {
        when(chainWriter.isOwn("0xext")).thenReturn(false);
        when(operateLogService.existsByTxHash("0xext")).thenReturn(false);

        subscriber.handleLog(log("0xext",
                topic("ProductRecalled(uint256,address)"), padded(5)));

        verify(productCache).evict(5L);
        verify(operateLogService).record(eq(0L), eq("system"), eq("CHAIN_EVENT"),
                eq(5L), eq("0xext"), contains("ProductRecalled"));
    }

    @Test
    void replayedKnownTransactionShouldSkipAudit() {
        // 重启后内存自有集合为空，历史交易靠审计表去重
        when(chainWriter.isOwn("0xold")).thenReturn(false);
        when(operateLogService.existsByTxHash("0xold")).thenReturn(true);

        subscriber.handleLog(log("0xold",
                topic("ProductRegistered(uint256,string,address)"), padded(5)));

        verify(productCache).evict(5L);
        verify(operateLogService, never()).record(anyLong(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    void externalRoleEventShouldAuditWithoutEvict() {
        when(chainWriter.isOwn("0xext-role")).thenReturn(false);
        when(operateLogService.existsByTxHash("0xext-role")).thenReturn(false);

        subscriber.handleLog(log("0xext-role",
                topic("RoleUpdated(address,uint8)"),
                "0x" + "0".repeat(24) + "a04fc4129c2895f1dad672d85eff21d8e371f867"));

        verify(productCache, never()).evict(anyLong());
        verify(operateLogService).record(eq(0L), eq("system"), eq("CHAIN_EVENT"),
                isNull(), eq("0xext-role"), contains("RoleUpdated"));
    }

    @Test
    void unknownTopicShouldAuditAsUnknown() {
        when(chainWriter.isOwn("0xunk")).thenReturn(false);
        when(operateLogService.existsByTxHash("0xunk")).thenReturn(false);

        subscriber.handleLog(log("0xunk", topic("SomethingElse(uint256)"), padded(1)));

        verify(productCache, never()).evict(anyLong());
        verify(operateLogService).record(anyLong(), anyString(), eq("CHAIN_EVENT"),
                isNull(), eq("0xunk"), contains("Unknown"));
    }

    @Test
    void nonZeroStatusShouldNotTouchCacheOrAudit() {
        subscriber.onReceiveLog("record", 1, List.of());

        verifyNoInteractions(productCache, operateLogService);
    }
}
