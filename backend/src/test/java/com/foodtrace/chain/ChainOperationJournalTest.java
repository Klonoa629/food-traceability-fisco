package com.foodtrace.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodtrace.common.BizException;
import com.foodtrace.entity.ChainOperation;
import com.foodtrace.mapper.ChainOperationMapper;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosTransactionReceipt;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 写链恢复测试：广播前记账、超时恢复与幂等内容约束
 *
 * @author Microft0629
 * @since 2026-10-11
 */
class ChainOperationJournalTest {
    private final ChainOperationMapper mapper = mock(ChainOperationMapper.class);
    private final Client client = mock(Client.class);
    private final ChainOperationJournal journal = new ChainOperationJournal(mapper, client, new ObjectMapper());
    private final Map<String, ChainOperation> rows = new HashMap<>();
    @SuppressWarnings("unchecked")
    private final Supplier<ChainOperationJournal.SignedTransaction> prepare = mock(Supplier.class);

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "request-0123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(client.getGroup()).thenReturn("group0");
        when(mapper.selectById(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(mapper.insert(any(ChainOperation.class))).thenAnswer(call -> {
            ChainOperation row = call.getArgument(0);
            rows.put(row.getOperationKey(), row);
            return 1;
        });
        when(prepare.get()).thenReturn(new ChainOperationJournal.SignedTransaction("0xtx", "0xsigned"));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private BcosTransactionReceipt success() {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash("0xtx");
        receipt.setStatus(0);
        return new BcosTransactionReceipt() {
            @Override
            public TransactionReceipt getTransactionReceipt() {
                return receipt;
            }
        };
    }

    private TransactionReceipt send(List<Object> args) {
        return journal.execute("farm", "contract", "addRecord", args, prepare);
    }

    @Test
    void retryShouldReturnOriginalReceiptWithoutSigningAgain() {
        when(client.getTransactionReceipt("0xtx", false)).thenReturn(null, success());
        when(client.sendTransaction("0xsigned", false)).thenReturn(success());
        send(List.of(1));
        send(List.of(1));
        verify(prepare, times(1)).get();
        verify(client, times(1)).sendTransaction("0xsigned", false);
    }

    @Test
    void databaseFailureBeforeBroadcastShouldNeverSend() {
        when(mapper.insert(any(ChainOperation.class))).thenThrow(new IllegalStateException("数据库中断"));
        assertThatThrownBy(() -> send(List.of(1))).isInstanceOf(IllegalStateException.class);
        verify(client, never()).sendTransaction(anyString(), anyBoolean());
    }

    @Test
    void broadcastTimeoutShouldRecoverByOriginalHash() {
        when(client.sendTransaction("0xsigned", false)).thenThrow(new IllegalStateException("超时"));
        assertThatThrownBy(() -> send(List.of(1))).isInstanceOf(BizException.class)
                .hasMessageContaining("结果待确认");
        when(client.getTransactionReceipt("0xtx", false)).thenReturn(success());
        assertThat(send(List.of(1)).getTransactionHash()).isEqualTo("0xtx");
        verify(prepare, times(1)).get();
        verify(client, times(1)).sendTransaction("0xsigned", false);
    }

    @Test
    void sameKeyWithDifferentContentShouldBeRejected() {
        when(client.sendTransaction("0xsigned", false)).thenReturn(success());
        send(List.of(1));
        assertThatThrownBy(() -> send(List.of(2))).isInstanceOf(BizException.class)
                .hasMessageContaining("不同内容");
        verify(prepare, times(1)).get();
    }

    @Test
    void pendingRetryShouldResendIdenticalSignedBytes() {
        when(client.sendTransaction("0xsigned", false)).thenReturn(null, success());
        assertThatThrownBy(() -> send(List.of(1))).isInstanceOf(BizException.class);
        send(List.of(1));
        verify(prepare, times(1)).get();
        verify(client, times(2)).sendTransaction("0xsigned", false);
    }

    @Test
    void missingRequestKeyShouldNotPrepareTransaction() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertThatThrownBy(() -> send(List.of(1))).isInstanceOf(BizException.class)
                .hasMessageContaining("Idempotency-Key");
        verify(prepare, never()).get();
    }

    @Test
    void resultWriteFailureShouldPreserveOriginalOperation() {
        when(client.sendTransaction("0xsigned", false)).thenReturn(success());
        doThrow(new IllegalStateException("数据库中断")).when(mapper).outcome(any(), eq("CONFIRMED"), isNull());
        assertThatThrownBy(() -> send(List.of(1))).isInstanceOf(BizException.class);
        doNothing().when(mapper).outcome(any(), eq("CONFIRMED"), isNull());
        when(client.getTransactionReceipt("0xtx", false)).thenReturn(success());
        send(List.of(1));
        verify(prepare, times(1)).get();
        verify(client, times(1)).sendTransaction(anyString(), anyBoolean());
    }
}
