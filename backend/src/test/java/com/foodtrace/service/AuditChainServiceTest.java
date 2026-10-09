package com.foodtrace.service;

import com.foodtrace.dto.AuditChainVO;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.mapper.OperateLogMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计哈希链测试：完整性通过、删行检出、篡改检出与确定性
 *
 * @author Microft0629
 * @since 2026-10-09
 */
@ExtendWith(MockitoExtension.class)
class AuditChainServiceTest {
    @Mock
    private OperateLogMapper operateLogMapper;

    private AuditChainService service;

    /**
     * 构造被测服务
     */
    @BeforeEach
    void setUp() {
        service = new AuditChainService(operateLogMapper, new SimpleMeterRegistry());
    }

    /**
     * 构造一行审计
     */
    private OperateLog row(long id, String detail) {
        OperateLog row = new OperateLog();
        row.setId(id);
        row.setUserId(1L);
        row.setUsername("regulator");
        row.setAction("LOGIN");
        row.setTargetId(null);
        row.setChainTxHash(null);
        row.setDetail(detail);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 9, 23, 0, 0));
        return row;
    }

    /**
     * 生成一段 n 行的完整链
     */
    private List<OperateLog> chain(int n) {
        String prev = AuditChainService.GENESIS;
        java.util.ArrayList<OperateLog> rows = new java.util.ArrayList<>();
        for (long i = 1; i <= n; i++) {
            OperateLog r = row(i, "操作" + i);
            r.setPrevHash(prev);
            r.setRowHash(service.hash(prev, r));
            prev = r.getRowHash();
            rows.add(r);
        }
        return rows;
    }

    @Test
    void intactChainShouldPass() {
        AuditChainVO result = service.verifyRows(chain(5));

        assertThat(result.intact()).isTrue();
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.firstBrokenId()).isNull();
    }

    @Test
    void deletedRowShouldBreakLink() {
        List<OperateLog> rows = chain(5);
        rows.remove(2); // 删除 id=3

        AuditChainVO result = service.verifyRows(rows);

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenId()).isEqualTo(4L);
        assertThat(result.reason()).contains("疑似删行");
    }

    @Test
    void tamperedDetailShouldBreakHash() {
        List<OperateLog> rows = chain(5);
        rows.get(2).setDetail("操作3-被篡改");

        AuditChainVO result = service.verifyRows(rows);

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenId()).isEqualTo(3L);
        assertThat(result.reason()).contains("疑似篡改");
    }

    @Test
    void unlinkedRowShouldFail() {
        List<OperateLog> rows = chain(3);
        rows.get(1).setRowHash(null);

        AuditChainVO result = service.verifyRows(rows);

        assertThat(result.intact()).isFalse();
        assertThat(result.reason()).contains("未接入哈希链");
    }

    @Test
    void hashShouldBeDeterministicAndChainSensitive() {
        OperateLog a = row(1, "同一操作");
        OperateLog b = row(1, "同一操作");
        assertThat(service.hash(AuditChainService.GENESIS, a))
                .isEqualTo(service.hash(AuditChainService.GENESIS, b))
                .hasSize(64);
        // 前向不同则哈希不同：同一行接在不同链尾结果不一样
        assertThat(service.hash(AuditChainService.GENESIS, a))
                .isNotEqualTo(service.hash("abc", a));
    }
}
