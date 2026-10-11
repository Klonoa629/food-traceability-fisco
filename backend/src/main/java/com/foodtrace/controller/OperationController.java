package com.foodtrace.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodtrace.common.Result;
import com.foodtrace.entity.ChainOperation;
import com.foodtrace.mapper.ChainOperationMapper;
import com.foodtrace.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前账户的最近写链结果，仅返回核对所需字段
 *
 * @author Microft0629
 * @since 2026-10-11
 */
@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationController {
    private final ChainOperationMapper mapper;

    public record OperationVO(String requestId, String method, String txHash, String status,
                              int attempts, String lastError, LocalDateTime createdAt) {}

    @GetMapping
    public Result<List<OperationVO>> list(@AuthenticationPrincipal LoginUser user) {
        if (user.signUserId() == null) {
            return Result.ok(List.of());
        }
        return Result.ok(mapper.selectList(new LambdaQueryWrapper<ChainOperation>()
                .eq(ChainOperation::getSignUserId, user.signUserId())
                .orderByDesc(ChainOperation::getCreatedAt).last("LIMIT 50")).stream()
                .map(row -> new OperationVO(row.getRequestId(), row.getMethod(), row.getTxHash(),
                        row.getStatus(), row.getAttempts(), row.getLastError(), row.getCreatedAt()))
                .toList());
    }
}
