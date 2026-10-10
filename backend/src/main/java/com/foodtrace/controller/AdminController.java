package com.foodtrace.controller;

import com.foodtrace.common.Result;
import com.foodtrace.dto.ApproveRequest;
import com.foodtrace.dto.AuditChainVO;
import com.foodtrace.dto.PageVO;
import com.foodtrace.dto.ProductVO;
import com.foodtrace.dto.RecallRequest;
import com.foodtrace.dto.UserInfo;
import com.foodtrace.entity.OperateLog;
import com.foodtrace.security.LoginUser;
import com.foodtrace.service.AuditAnchorTask;
import com.foodtrace.service.AuditChainService;
import com.foodtrace.service.OperateLogService;
import com.foodtrace.service.ProductService;
import com.foodtrace.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 监管管理接口（仅 REGULATOR 角色可访问）
 *
 * @author Microft0629
 * @since 2026-09-17
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    /** 账户服务 */
    private final UserService userService;
    /** 审计服务 */
    private final OperateLogService operateLogService;
    /** 审计哈希链服务 */
    private final AuditChainService auditChainService;
    /** 审计锚定任务 */
    private final AuditAnchorTask auditAnchorTask;
    /** 产品服务 */
    private final ProductService productService;

    /**
     * 查询账户列表
     *
     * @param status 账户状态过滤（可空：0 = 待审批，1 = 生效，2 = 已吊销）
     * @return 账户信息列表
     */
    @GetMapping("/users")
    public Result<List<UserInfo>> listUsers(@RequestParam(required = false) Integer status) {
        return Result.ok(userService.list(status));
    }

    /**
     * 审批账户：Sign 开托管户、链上发角色并使账户生效
     *
     * @param id       待审批账户 id
     * @param request  审批请求（审批角色）
     * @param operator 当前监管账户
     * @return 生效后的账户信息
     */
    @PostMapping("/users/{id}/approve")
    public Result<UserInfo> approve(@PathVariable Long id,
                                    @Valid @RequestBody ApproveRequest request,
                                    @AuthenticationPrincipal LoginUser operator) {
        return Result.ok(userService.approve(id, request.role(), operator));
    }

    /**
     * 吊销账户：链上收角色并将账户置为已吊销
     *
     * @param id       生效账户 id
     * @param operator 当前监管账户
     * @return 吊销后的账户信息
     */
    @PostMapping("/users/{id}/revoke")
    public Result<UserInfo> revoke(@PathVariable Long id,
                                   @AuthenticationPrincipal LoginUser operator) {
        return Result.ok(userService.revoke(id, operator));
    }

    /**
     * 召回产品（链上进入召回终态）
     *
     * @param id       产品 id
     * @param request  召回请求
     * @param operator 当前监管账户
     * @return 更新后的产品
     */
    @PostMapping("/products/{id}/recall")
    public Result<ProductVO> recall(@PathVariable long id,
                                    @Valid @RequestBody RecallRequest request,
                                    @AuthenticationPrincipal LoginUser operator) {
        return Result.ok(productService.recall(id, request, operator));
    }

    /**
     * 分页查询操作审计记录
     *
     * @param action 操作类型过滤（可空）
     * @param userId 操作账户 id 过滤（可空）
     * @param page   页码，从 1 起
     * @param size   每页条数
     * @return 按时间倒序的分页审计记录
     */
    @GetMapping("/logs")
    public Result<PageVO<OperateLog>> listLogs(@RequestParam(required = false) String action,
                                               @RequestParam(required = false) Long userId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return Result.ok(operateLogService.list(action, userId, page, size));
    }

    /**
     * 链上校验审计记录：比对数据库中的交易哈希与链上实际交易
     *
     * @return 审计记录 id -> 是否与链上一致
     */
    @GetMapping("/logs/verify")
    public Result<Map<Long, Boolean>> verifyLogs() {
        return Result.ok(operateLogService.verifyOnChain());
    }

    /**
     * 校验审计哈希链完整性，检出删行与篡改
     *
     * @return 链校验结果
     */
    @GetMapping("/logs/verify-chain")
    public Result<AuditChainVO> verifyAuditChain() {
        return Result.ok(auditChainService.verify());
    }

    /**
     * 校验审计链头与链上锚定是否一致
     *
     * @return null 表示一致，非 null 为差异描述
     */
    @GetMapping("/logs/verify-anchor")
    public Result<String> verifyAnchor() {
        return Result.ok(auditAnchorTask.verifyAnchor());
    }
}
