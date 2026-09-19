package com.foodtrace.chain;

import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple2;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.codec.decode.RevertMessageParser;

/**
 * 失败回执描述工具
 *
 * <p>合约 revert 时节点只在回执 output 里携带 Error(string) 编码的原因文本，
 * 本工具用 SDK 解析器还原具体原因，便于接口直接透出给调用方。
 *
 * @author Microft0629
 * @since 2026-09-18
 */
public final class RevertReason {
    /**
     * 工具类禁止实例化
     */
    private RevertReason() {
    }

    /**
     * 描述一笔失败回执：优先还原 revert 原因，否则回退到原始 message
     *
     * @param receipt 交易回执（可为 null）
     * @return 形如 "status=16：Product has been recalled" 的描述
     */
    public static String describe(TransactionReceipt receipt) {
        if (receipt == null) {
            return "无回执";
        }
        String base = "status=" + receipt.getStatus();
        try {
            Tuple2<Boolean, String> resolved = RevertMessageParser.tryResolveRevertMessage(receipt);
            if (Boolean.TRUE.equals(resolved.getValue1())) {
                return base + "：" + resolved.getValue2();
            }
        } catch (Exception ignored) {
            // 解析失败则回退原始信息
        }
        String message = receipt.getMessage();
        return message == null || message.isBlank() ? base : base + " " + message;
    }
}
