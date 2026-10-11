package com.foodtrace.service;

import com.foodtrace.chain.ChainAnchorService;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.entity.SysUser;
import com.foodtrace.mapper.OperateLogMapper;
import com.foodtrace.mapper.SysUserMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 锚定回归测试：正常追加、历史删改与快照一致性
 *
 * @author Microft0629
 * @since 2026-10-11
 */
class AuditAnchorTaskTest {
    private final OperateLogMapper logs = mock(OperateLogMapper.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final ChainAnchorService chain = mock(ChainAnchorService.class);
    private final AuditChainService hashes = new AuditChainService(logs, new SimpleMeterRegistry());
    private AuditAnchorTask task;

    @BeforeEach
    void setUp() {
        when(chain.configured()).thenReturn(true);
        task = new AuditAnchorTask(logs, users, chain, hashes, new SimpleMeterRegistry());
    }

    private List<OperateLog> rows(int count) {
        List<OperateLog> rows = new ArrayList<>();
        String prev = AuditChainService.GENESIS;
        for (long i = 1; i <= count; i++) {
            OperateLog row = new OperateLog();
            row.setId(i * 2); // 自增 id 有空洞时也不能当作行数
            row.setUserId(1L);
            row.setUsername("regulator");
            row.setAction("LOGIN");
            row.setDetail("登录" + i);
            row.setCreatedAt(LocalDateTime.of(2026, 10, 11, 12, 0));
            row.setPrevHash(prev);
            row.setRowHash(hashes.hash(prev, row));
            prev = row.getRowHash();
            rows.add(row);
        }
        return rows;
    }

    private void anchored(List<OperateLog> rows, int count) {
        when(logs.selectList(any())).thenReturn(rows);
        when(chain.getLatestAnchor()).thenReturn(
                new ChainAnchorService.AnchorRecord(rows.get(count - 1).getRowHash(), count, 0));
    }

    @Test
    void normalAppendShouldNotInvalidateHistoricalAnchor() {
        List<OperateLog> rows = rows(4);
        anchored(rows, 2);
        assertThat(task.verifyAnchor()).isNull();
    }

    @Test
    void tamperedHistoricalContentShouldFailEvenWhenStoredHashIsUnchanged() {
        List<OperateLog> rows = rows(4);
        anchored(rows, 2);
        rows.get(0).setDetail("伪造内容");
        assertThat(task.verifyAnchor()).contains("已锚定日志不完整");
    }

    @Test
    void deletingAnchoredTailShouldFailEvenAfterAppending() {
        List<OperateLog> rows = rows(4);
        anchored(rows, 2);
        rows.remove(1);
        assertThat(task.verifyAnchor()).isNotNull();
    }

    @Test
    void truncationBelowAnchorShouldFail() {
        List<OperateLog> rows = rows(2);
        anchored(rows, 2);
        rows.remove(1);
        assertThat(task.verifyAnchor()).contains("锚定行数异常");
    }

    @Test
    void rewrittenChainShouldNotMatchAnchor() {
        List<OperateLog> rows = rows(2);
        anchored(rows, 2);
        rows.get(0).setDetail("重写");
        String prev = AuditChainService.GENESIS;
        for (OperateLog row : rows) {
            row.setPrevHash(prev);
            row.setRowHash(hashes.hash(prev, row));
            prev = row.getRowHash();
        }
        assertThat(task.verifyAnchor()).contains("链头不一致");
    }

    @Test
    void anchorShouldUseOneSnapshotForCountAndHead() {
        List<OperateLog> rows = rows(3);
        when(logs.selectList(any())).thenReturn(rows);
        SysUser regulator = new SysUser();
        regulator.setSignUserId("regulator");
        when(users.selectOne(any())).thenReturn(regulator);
        task.anchor();
        verify(chain).anchor("regulator", rows.get(2).getRowHash(), 3);
        verify(logs, never()).selectCount(any());
    }

    @Test
    void brokenChainShouldNotBeAnchored() {
        List<OperateLog> rows = rows(2);
        rows.get(1).setRowHash(null);
        when(logs.selectList(any())).thenReturn(rows);
        task.anchor();
        verify(chain, never()).anchor(anyString(), anyString(), anyLong());
    }

    @Test
    void disabledAnchorShouldNotPrepareOrSendTransaction() {
        when(chain.configured()).thenReturn(false);
        task.anchor();
        verifyNoInteractions(logs, users);
        verify(chain, never()).anchor(anyString(), anyString(), anyLong());
        assertThat(task.verifyAnchor()).contains("尚未配置");
    }
}
