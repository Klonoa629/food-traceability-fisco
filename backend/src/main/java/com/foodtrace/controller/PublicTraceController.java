package com.foodtrace.controller;

import com.foodtrace.chain.ProductCache;
import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.common.Result;
import com.foodtrace.dto.ProductVO;
import com.foodtrace.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开溯源查询接口（无需登录，消费者按批次号查验）
 *
 * <p>按 IP 限速防止批量爬取。
 *
 * @author Microft0629
 * @since 2026-09-24
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicTraceController {
    /** 链上产品读缓存 */
    private final ProductCache productCache;
    /** 限流服务 */
    private final RateLimitService rateLimitService;

    /**
     * 按批次号查询产品完整溯源信息
     *
     * @param batchNo 产品批次号
     * @param request 当前请求
     * @return 产品视图(含全部溯源记录)
     */
    @GetMapping("/trace")
    public Result<ProductVO> trace(@RequestParam String batchNo, HttpServletRequest request) {
        rateLimitService.checkIpLimit(request, "public");
        if (batchNo == null || batchNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "批次号不能为空");
        }
        try {
            return Result.ok(productCache.getByBatch(batchNo.trim()));
        } catch (IllegalStateException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "未找到该批次号对应的产品");
        }
    }
}
