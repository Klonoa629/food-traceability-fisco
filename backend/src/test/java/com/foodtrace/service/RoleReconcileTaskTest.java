package com.foodtrace.service;

import com.foodtrace.chain.ChainReader;
import com.foodtrace.entity.SysUser;
import com.foodtrace.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 角色对账任务测试：不一致识别、链故障中止与一致时静默
 *
 * @author Microft0629
 * @since 2026-10-06
 */
@ExtendWith(MockitoExtension.class)
class RoleReconcileTaskTest {
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OperateLogService operateLogService;
    @Mock
    private ChainReader chainReader;

    private RoleReconcileTask task;

    /**
     * 构造被测任务
     */
    @BeforeEach
    void setUp() {
        task = new RoleReconcileTask(userMapper, operateLogService, chainReader);
    }

    /**
     * 构造带链上身份的账户
     */
    private SysUser user(long id, String username, int role, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus(status);
        user.setIsRegulator(false);
        user.setChainAddress("0x" + String.format("%040x", id));
        return user;
    }

    @Test
    void consistentRolesShouldStaySilent() {
        when(userMapper.selectList(any())).thenReturn(List.of(user(2L, "farm_a", 1, 1)));
        when(chainReader.roles(anyString())).thenReturn(1);

        task.reconcile();

        verify(operateLogService, never()).record(anyLong(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    void mismatchedRoleShouldBeAudited() {
        when(userMapper.selectList(any())).thenReturn(List.of(user(2L, "farm_a", 2, 1)));
        when(chainReader.roles(anyString())).thenReturn(1);

        task.reconcile();

        verify(operateLogService).record(eq(0L), eq("system"), eq("ROLE_MISMATCH"),
                eq(2L), isNull(), contains("库内角色 2，链上角色 1"));
    }

    @Test
    void revokedUserWithLiveChainRoleShouldBeAudited() {
        when(userMapper.selectList(any())).thenReturn(List.of(user(3L, "inspector_b", 3, 2)));
        when(chainReader.roles(anyString())).thenReturn(3);

        task.reconcile();

        verify(operateLogService).record(eq(0L), eq("system"), eq("ROLE_MISMATCH"),
                eq(3L), isNull(), contains("库内角色 0，链上角色 3"));
    }

    @Test
    void chainFailureShouldAbortRoundWithoutFalseAlarms() {
        when(userMapper.selectList(any())).thenReturn(List.of(user(2L, "farm_a", 1, 1)));
        when(chainReader.roles(anyString())).thenThrow(new IllegalStateException("节点不可达"));

        task.reconcile();

        verify(operateLogService, never()).record(anyLong(), anyString(), anyString(),
                any(), any(), any());
    }
}
