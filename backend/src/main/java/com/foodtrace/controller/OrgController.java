package com.foodtrace.controller;

import com.foodtrace.common.Result;
import com.foodtrace.dto.UserInfo;
import com.foodtrace.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 参与机构接口（需认证，供交接时选择下游机构）
 *
 * @author Microft0629
 * @since 2026-09-18
 */
@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class OrgController {
    /** 账户服务 */
    private final UserService userService;

    /**
     * 查询生效中的参与机构
     *
     * @return 生效的非监管账户列表
     */
    @GetMapping
    public Result<List<UserInfo>> listActiveOrgs() {
        return Result.ok(userService.listActiveOrgs());
    }
}
