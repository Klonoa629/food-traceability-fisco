package com.foodtrace.service;

import com.foodtrace.config.ContractProperties;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.entity.SysUser;
import com.foodtrace.mapper.OperateLogMapper;
import com.foodtrace.mapper.SysUserMapper;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosTransactionReceipt;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 审计链上校验单元测试：事件签名、首个索引参数与回执状态的核对
 *
 * @author Microft0629
 * @since 2026-09-30
 */
@ExtendWith(MockitoExtension.class)
class OperateLogServiceTest {
    private static final String CONTRACT = "0x2af6160e266f763652f433a80b94fc13f4065303";

    @Mock
    private OperateLogMapper operateLogMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private AuditChainService auditChainService;
    @Mock
    private Client client;

    private ContractProperties contractProperties;
    private OperateLogService service;
    private final CryptoSuite cryptoSuite = new CryptoSuite(CryptoType.ECDSA_TYPE);

    /**
     * 构造被测服务与合约配置
     */
    @BeforeEach
    void setUp() {
        contractProperties = new ContractProperties();
        contractProperties.setContractAddress(CONTRACT);
        service = new OperateLogService(operateLogMapper, auditChainService,
                userMapper, client, contractProperties);
    }

    /**
     * 构造一条审计记录
     */
    private OperateLog entry(long id, String action, Long targetId, String txHash) {
        OperateLog entry = new OperateLog();
        entry.setId(id);
        entry.setAction(action);
        entry.setTargetId(targetId);
        entry.setChainTxHash(txHash);
        return entry;
    }

    /**
     * 构造事件主题
     */
    private String topic(String eventSignature) {
        return Hex.toHexStringWithPrefix(
                cryptoSuite.hash(eventSignature.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 构造携带单条事件日志的回执响应
     */
    private void stubReceipt(String txHash, int status, String eventLogAddress,
                             String topic0, String topic1) {
        TransactionReceipt.Logs eventLog = new TransactionReceipt.Logs();
        eventLog.setAddress(eventLogAddress);
        eventLog.setTopics(List.of(topic0, topic1));
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus(status);
        receipt.setLogEntries(List.of(eventLog));
        BcosTransactionReceipt response = new BcosTransactionReceipt() {
            @Override
            public TransactionReceipt getTransactionReceipt() {
                return receipt;
            }
        };
        when(client.getTransactionReceipt(eq(txHash), eq(false))).thenReturn(response);
    }

    @Test
    void productActionWithMatchingEventShouldVerify() {
        when(operateLogMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(entry(11, "ADD_RECORD", 3L, "0xtx1")));
        stubReceipt("0xtx1", 0, CONTRACT, topic("RecordAdded(uint256,uint8,address)"),
                String.format("0x%064x", 3L));

        assertThat(service.verifyOnChain()).containsEntry(11L, true);
    }

    @Test
    void productActionWithWrongProductIdShouldFail() {
        when(operateLogMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(entry(12, "RECALL", 3L, "0xtx2")));
        stubReceipt("0xtx2", 0, CONTRACT, topic("ProductRecalled(uint256,address)"),
                String.format("0x%064x", 999L));

        assertThat(service.verifyOnChain()).containsEntry(12L, false);
    }

    @Test
    void failedReceiptShouldFailEvenWithMatchingEvent() {
        when(operateLogMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(entry(13, "HANDOVER", 1L, "0xtx3")));
        stubReceipt("0xtx3", 16, CONTRACT, topic("ProductHandedOver(uint256,address,address)"),
                String.format("0x%064x", 1L));

        assertThat(service.verifyOnChain()).containsEntry(13L, false);
    }

    @Test
    void approveUserShouldMatchAccountAddress() {
        SysUser target = new SysUser();
        target.setId(2L);
        target.setChainAddress("0xa04fc4129c2895f1dad672d85eff21d8e371f867");
        when(userMapper.selectById(2L)).thenReturn(target);
        when(operateLogMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(entry(14, "APPROVE_USER", 2L, "0xtx4")));
        // 链上回执的地址不带 0x 前缀，校验需兼容两种写法
        stubReceipt("0xtx4", 0, CONTRACT.substring(2), topic("RoleUpdated(address,uint8)"),
                "0x" + "0".repeat(24) + "a04fc4129c2895f1dad672d85eff21d8e371f867");

        assertThat(service.verifyOnChain()).containsEntry(14L, true);
    }
}
