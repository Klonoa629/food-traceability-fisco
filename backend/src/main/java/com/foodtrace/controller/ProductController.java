package com.foodtrace.controller;

import com.foodtrace.common.Result;
import com.foodtrace.dto.*;
import com.foodtrace.security.LoginUser;
import com.foodtrace.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品溯源接口（需认证，链上权限由合约裁决）
 *
 * @author Microft0629
 * @since 2026-09-18
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    /** 产品业务服务 */
    private final ProductService productService;

    /**
     * 注册产品（仅基地机构）
     *
     * @param request 注册请求
     * @param user    操作账户
     * @return 注册后的产品
     */
    @PostMapping
    public Result<ProductVO> register(@Valid @RequestBody RegisterProductRequest request,
                                      @AuthenticationPrincipal LoginUser user) {
        return Result.ok(productService.register(request, user));
    }

    /**
     * 查询全部产品，新注册的在前
     *
     * @return 产品列表
     */
    @GetMapping
    public Result<List<ProductVO>> list() {
        return Result.ok(productService.list());
    }

    /**
     * 查询产品完整溯源信息
     *
     * @param id 产品 id
     * @return 产品视图
     */
    @GetMapping("/{id}")
    public Result<ProductVO> get(@PathVariable long id) {
        return Result.ok(productService.get(id));
    }

    /**
     * 添加环节记录（仅当前责任方）
     *
     * @param id      产品 id
     * @param request 记录请求
     * @param user    操作账户
     * @return 更新后的产品
     */
    @PostMapping("/{id}/records")
    public Result<ProductVO> addRecord(@PathVariable long id,
                                       @Valid @RequestBody AddRecordRequest request,
                                       @AuthenticationPrincipal LoginUser user) {
        return Result.ok(productService.addRecord(id, request, user));
    }

    /**
     * 产品交接（仅当前责任方）
     *
     * @param id      产品 id
     * @param request 交接请求
     * @param user    操作账户
     * @return 更新后的产品
     */
    @PostMapping("/{id}/handover")
    public Result<ProductVO> handOver(@PathVariable long id,
                                      @Valid @RequestBody HandoverRequest request,
                                      @AuthenticationPrincipal LoginUser user) {
        return Result.ok(productService.handOver(id, request, user));
    }

    /**
     * 质检裁决（仅质检机构）
     *
     * @param id      产品 id
     * @param request 质检请求
     * @param user    操作账户
     * @return 更新后的产品
     */
    @PostMapping("/{id}/inspect")
    public Result<ProductVO> inspect(@PathVariable long id,
                                     @Valid @RequestBody InspectRequest request,
                                     @AuthenticationPrincipal LoginUser user) {
        return Result.ok(productService.inspect(id, request, user));
    }
}
