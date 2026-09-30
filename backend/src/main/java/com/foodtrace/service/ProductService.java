package com.foodtrace.service;

import com.foodtrace.chain.ChainProductService;
import com.foodtrace.chain.ChainReader;
import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.dto.*;
import com.foodtrace.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 产品溯源业务服务
 *
 * <p>在链上交易前做本地前置校验（角色匹配等）以快速失败，
 * 上链成功后回读产品并落审计；操作被拒绝时以 <code>动作_FAILED</code>
 * 落失败审计（含拒绝原因）；哈希字段缺省时由后端对业务内容计算 SHA-256。
 *
 * @author Microft0629
 * @since 2026-09-18
 */
@Service
@RequiredArgsConstructor
public class ProductService {
    /** 角色到环节的映射（与合约 roleToStage 一致），键为合约角色 1-6 */
    private static final Map<Integer, Integer> STAGE_OF_ROLE = Map.of(
            1, 0, // 基地 -> 种植
            2, 1, // 加工厂 -> 加工
            3, 2, // 质检 -> 质检
            4, 3, // 物流 -> 运输
            5, 4, // 仓库 -> 仓储
            6, 5  // 零售 -> 销售
    );
    /** 销售环节，达到后视为流转终态（召回为 6，自然大于该值） */
    private static final int STAGE_ON_SALE = 5;

    /** 链上产品交易封装 */
    private final ChainProductService chainProductService;
    /** 链上只读查询 */
    private final ChainReader chainReader;
    /** 审计服务 */
    private final OperateLogService operateLogService;

    /**
     * 注册产品（仅基地角色），成功后返回链上产品
     *
     * @param request 注册请求
     * @param user    操作账户
     * @return 注册后的产品视图
     * @throws BizException 非基地角色或上链失败时抛出
     */
    public ProductVO register(RegisterProductRequest request, LoginUser user) {
        try {
            return doRegister(request, user);
        } catch (BizException e) {
            operateLogService.record(user.id(), user.username(),
                    "REGISTER_PRODUCT_FAILED", null, null, e.getMessage());
            throw e;
        }
    }

    private ProductVO doRegister(RegisterProductRequest request, LoginUser user) {
        requireChainIdentity(user);
        requireRole(user, 1, "仅基地机构可注册产品");
        String dataHash = orSha256(request.dataHash(),
                request.name() + "|" + request.batchNo() + "|" + request.description() + "|" + request.location());
        String txHash = chainProductService.registerProduct(user.signUserId(), request.name(),
                request.batchNo(), request.description(), request.location(), dataHash);
        ProductVO product = chainReader.productByBatch(request.batchNo());
        audit(user, "REGISTER_PRODUCT", product.id(), txHash,
                "注册产品 " + request.name() + "，批次 " + request.batchNo());
        return product;
    }

    /**
     * 添加环节记录（仅当前责任方，环节须与自身角色匹配）
     *
     * @param productId 产品 id
     * @param request   记录请求
     * @param user      操作账户
     * @return 更新后的产品视图
     * @throws BizException 角色不符、越权或上链失败时抛出
     */
    public ProductVO addRecord(long productId, AddRecordRequest request, LoginUser user) {
        try {
            return doAddRecord(productId, request, user);
        } catch (BizException e) {
            audit(user, "ADD_RECORD_FAILED", productId, null, e.getMessage());
            throw e;
        }
    }

    private ProductVO doAddRecord(long productId, AddRecordRequest request, LoginUser user) {
        requireChainIdentity(user);
        requireStageMatchesRole(user, request.stage());
        String dataHash = orSha256(request.dataHash(),
                productId + "|" + request.stage() + "|" + request.description() + "|" + request.location());
        String txHash = chainProductService.addRecord(user.signUserId(), productId, request.stage(),
                request.description(), request.location(), dataHash);
        audit(user, "ADD_RECORD", productId, txHash,
                "添加环节记录 " + request.stage() + "：" + request.description());
        return chainReader.product(productId);
    }

    /**
     * 产品交接（仅当前责任方），流转规则由合约状态机裁决
     *
     * @param productId 产品 id
     * @param request   交接请求
     * @param user      操作账户
     * @return 更新后的产品视图
     * @throws BizException 越权或上链失败时抛出
     */
    public ProductVO handOver(long productId, HandoverRequest request, LoginUser user) {
        try {
            return doHandOver(productId, request, user);
        } catch (BizException e) {
            audit(user, "HANDOVER_FAILED", productId, null, e.getMessage());
            throw e;
        }
    }

    private ProductVO doHandOver(long productId, HandoverRequest request, LoginUser user) {
        requireChainIdentity(user);
        String txHash = chainProductService.handOver(user.signUserId(), productId, request.nextHolder());
        audit(user, "HANDOVER", productId, txHash, "交接至 " + request.nextHolder());
        return chainReader.product(productId);
    }

    /**
     * 质检裁决（仅质检机构且为当前责任方）
     *
     * @param productId 产品 id
     * @param request   质检请求
     * @param user      操作账户
     * @return 更新后的产品视图
     * @throws BizException 非质检机构或上链失败时抛出
     */
    public ProductVO inspect(long productId, InspectRequest request, LoginUser user) {
        try {
            return doInspect(productId, request, user);
        } catch (BizException e) {
            audit(user, "INSPECT_FAILED", productId, null, e.getMessage());
            throw e;
        }
    }

    private ProductVO doInspect(long productId, InspectRequest request, LoginUser user) {
        requireChainIdentity(user);
        requireRole(user, 3, "仅质检机构可执行质检");
        String reportHash = orSha256(request.reportHash(), productId + "|" + request.qualified());
        String txHash = chainProductService.inspectProduct(user.signUserId(), productId,
                reportHash, request.qualified());
        audit(user, "INSPECT", productId, txHash,
                "质检" + (request.qualified() ? "合格" : "不合格"));
        return chainReader.product(productId);
    }

    /**
     * 产品召回（仅监管机构）
     *
     * @param productId 产品 id
     * @param request   召回请求
     * @param operator  监管账户
     * @return 更新后的产品视图
     * @throws BizException 非监管账户或上链失败时抛出
     */
    public ProductVO recall(long productId, RecallRequest request, LoginUser operator) {
        try {
            return doRecall(productId, request, operator);
        } catch (BizException e) {
            audit(operator, "RECALL_FAILED", productId, null, e.getMessage());
            throw e;
        }
    }

    private ProductVO doRecall(long productId, RecallRequest request, LoginUser operator) {
        if (!operator.regulator() || operator.signUserId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅监管机构可召回产品");
        }
        String reasonHash = orSha256(request.reasonHash(), productId + "|" + request.reason());
        String txHash = chainProductService.recallProduct(operator.signUserId(), productId, reasonHash);
        audit(operator, "RECALL", productId, txHash, "召回原因：" + request.reason());
        return chainReader.product(productId);
    }

    /**
     * 查询产品完整信息
     *
     * @param productId 产品 id
     * @return 产品视图
     * @throws BizException 产品不存在时抛出
     */
    public ProductVO get(long productId) {
        if (productId < 1 || productId > chainReader.productCount()) {
            throw new BizException(ErrorCode.NOT_FOUND, "产品不存在");
        }
        return chainReader.product(productId);
    }

    /**
     * 查询全部产品，新注册的在前
     *
     * @return 产品视图列表
     */
    public List<ProductVO> list() {
        List<ProductVO> products = new ArrayList<>();
        for (long id = chainReader.productCount(); id >= 1; id--) {
            products.add(chainReader.product(id));
        }
        return products;
    }

    /**
     * 查询机构名下仍在流转中的产品（未到销售/召回终态）
     *
     * @param chainAddress 机构链上地址
     * @return 在途产品列表，新的在前
     */
    public List<ProductVO> findInFlight(String chainAddress) {
        List<ProductVO> products = new ArrayList<>();
        for (long id = chainReader.productCount(); id >= 1; id--) {
            ProductVO product = chainReader.product(id);
            if (chainAddress.equalsIgnoreCase(product.currentHolder())
                    && product.stage() < STAGE_ON_SALE) {
                products.add(product);
            }
        }
        return products;
    }

    /**
     * 要求账户已绑定链上身份
     */
    private void requireChainIdentity(LoginUser user) {
        if (user.signUserId() == null || user.chainAddress() == null) {
            throw new BizException(ErrorCode.INVALID_STATE, "账户未绑定链上身份");
        }
    }

    /**
     * 要求账户持有指定角色
     */
    private void requireRole(LoginUser user, int role, String message) {
        if (user.role() != role) {
            throw new BizException(ErrorCode.FORBIDDEN, message);
        }
    }

    /**
     * 要求账户角色与目标环节匹配（与合约 roleToStage 规则一致）
     */
    private void requireStageMatchesRole(LoginUser user, int stage) {
        if (user.role() == 3) {
            throw new BizException(ErrorCode.FORBIDDEN, "质检机构请使用质检接口");
        }
        Integer mapped = STAGE_OF_ROLE.get(user.role());
        if (mapped == null || mapped != stage) {
            throw new BizException(ErrorCode.FORBIDDEN, "环节与机构角色不符");
        }
    }

    /**
     * 落一条操作审计
     */
    private void audit(LoginUser user, String action, long productId, String txHash, String detail) {
        operateLogService.record(user.id(), user.username(), action, productId, txHash, detail);
    }

    /**
     * 哈希兜底：调用方提供则原样使用，否则对内容计算 SHA-256
     */
    private static String orSha256(String provided, String content) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
