package com.foodtrace.chain;

import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.utils.Numeric;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 失败回执描述工具测试：Error(string) 回滚原因还原
 *
 * @author Microft0629
 * @since 2026-09-18
 */
class RevertReasonTest {

    /**
     * 构造 Error(string) 编码的 revert 回执
     */
    private TransactionReceipt revertReceipt(String reason) {
        String reasonHex = Numeric.toHexStringNoPrefix(
                reason.getBytes(StandardCharsets.UTF_8));
        // selector(4B) + offset(32B=0x20) + length(32B) + data(补齐 32B)
        String padded = reasonHex + "0".repeat(64 - reasonHex.length() % 64);
        String output = "0x08c379a0"
                + "0".repeat(62) + "20"
                + String.format("%064x", reason.getBytes(StandardCharsets.UTF_8).length)
                + padded;
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus(16);
        receipt.setOutput(output);
        receipt.setMessage("");
        return receipt;
    }

    @Test
    void shouldResolveRevertReasonText() {
        String described = RevertReason.describe(revertReceipt("Product has been recalled"));
        assertThat(described).isEqualTo("status=16：Product has been recalled");
    }

    @Test
    void shouldSupportChineseReason() {
        String described = RevertReason.describe(revertReceipt("产品已被召回"));
        assertThat(described).contains("产品已被召回");
    }

    @Test
    void shouldFallBackToRawMessageWhenNoRevertData() {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus(18);
        receipt.setMessage("out of gas");
        assertThat(RevertReason.describe(receipt)).isEqualTo("status=18 out of gas");
    }

    @Test
    void shouldDescribeNullReceipt() {
        assertThat(RevertReason.describe(null)).isEqualTo("无回执");
    }
}
