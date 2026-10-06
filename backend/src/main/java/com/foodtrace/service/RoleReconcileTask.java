package com.foodtrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodtrace.chain.ChainReader;
import com.foodtrace.entity.SysUser;
import com.foodtrace.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色对账定时任务
 *
 * <p>周期性比对库内角色与链上角色是否一致：生效账户期望等于
 * sys_user.role，已吊销账户期望为 0。发现不一致只记录告警日志与
 * ROLE_MISMATCH 审计，不自动修正——以哪一侧为准需人工判定。
 *
 * @author Microft0629
 * @since 2026-10-06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleReconcileTask {
    /** 账户 Mapper */
    private final SysUserMapper userMapper;
    /** 审计服务 */
    private final OperateLogService operateLogService;
    /** 链上只读查询 */
    private final ChainReader chainReader;

    /**
     * 对账入口，周期由 foodtrace.reconcile-interval-ms 配置（默认 10 分钟）
     */
    @Scheduled(fixedDelayString = "${foodtrace.reconcile-interval-ms:600000}")
    public void reconcile() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .isNotNull(SysUser::getChainAddress)
                .ne(SysUser::getIsRegulator, true));
        int mismatches = 0;
        for (SysUser user : users) {
            int chainRole;
            try {
                chainRole = chainReader.roles(user.getChainAddress());
            } catch (Exception e) {
                // 链不可达时中止本轮，避免把故障误判为所有账户不一致
                log.warn("角色对账中止：链上查询失败 {}", e.getMessage());
                return;
            }
            if (checkUser(user, chainRole)) {
                mismatches++;
            }
        }
        if (mismatches > 0) {
            log.warn("角色对账完成：{} 个账户的库内角色与链上不一致，详见 ROLE_MISMATCH 审计", mismatches);
        }
    }

    /**
     * 比对单个账户，不一致时落审计
     *
     * @param user      账户实体
     * @param chainRole 链上角色
     * @return 是否不一致
     */
    private boolean checkUser(SysUser user, int chainRole) {
        int expected = Integer.valueOf(1).equals(user.getStatus())
                ? user.getRole() : 0;
        if (chainRole == expected) {
            return false;
        }
        String detail = String.format("角色不一致：%s(id=%d) 库内角色 %d，链上角色 %d，状态 %d",
                user.getUsername(), user.getId(), expected, chainRole, user.getStatus());
        log.warn(detail);
        operateLogService.record(0L, "system", "ROLE_MISMATCH", user.getId(), null, detail);
        return true;
    }
}
