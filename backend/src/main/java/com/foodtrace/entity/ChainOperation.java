package com.foodtrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 写链操作记录，signedTx 仅供内部恢复，不作为接口响应返回
 *
 * @author Microft0629
 * @since 2026-10-11
 */
@Data
public class ChainOperation {
    @TableId(type = IdType.INPUT)
    private String operationKey;
    private String requestId;
    private String signUserId;
    private String method;
    private String payloadHash;
    private String txHash;
    private String signedTx;
    private String status;
    private Integer attempts;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
