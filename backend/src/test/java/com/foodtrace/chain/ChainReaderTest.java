package com.foodtrace.chain;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 链上时间戳解析测试：单位合理区间校验
 *
 * @author Microft0629
 * @since 2026-09-30
 */
class ChainReaderTest {

    @Test
    void shouldAcceptMillisecondTimestamp() {
        assertThat(ChainReader.timestampOf(1_789_700_451_972L)).isEqualTo(1_789_700_451_972L);
        assertThat(ChainReader.timestampOf(BigInteger.valueOf(1_789_700_451_972L)))
                .isEqualTo(1_789_700_451_972L);
    }

    @Test
    void shouldRejectSecondLevelTimestamp() {
        assertThatThrownBy(() -> ChainReader.timestampOf(1_789_700_451L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("疑似单位变化");
    }

    @Test
    void shouldRejectOutOfRangeTimestamp() {
        assertThatThrownBy(() -> ChainReader.timestampOf(0L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ChainReader.timestampOf(9_999_999_999_999L))
                .isInstanceOf(IllegalStateException.class);
    }
}
