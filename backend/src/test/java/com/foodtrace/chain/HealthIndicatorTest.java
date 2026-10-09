package com.foodtrace.chain;

import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.response.BlockNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 链与签名服务健康指示器测试
 *
 * @author Microft0629
 * @since 2026-10-09
 */
@ExtendWith(MockitoExtension.class)
class HealthIndicatorTest {
    @Mock
    private Client client;
    @Mock
    private SignClient signClient;
    @Mock
    private BlockNumber blockNumber;

    @Test
    void chainShouldBeUpWhenBlockNumberFetchable() {
        when(client.getBlockNumber()).thenReturn(blockNumber);
        when(blockNumber.getBlockNumber()).thenReturn(java.math.BigInteger.valueOf(69L));

        Health health = new ChainHealthIndicator(client).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("blockNumber", 69L);
    }

    @Test
    void chainShouldBeDownWhenNodeUnreachable() {
        when(client.getBlockNumber()).thenThrow(new IllegalStateException("节点不可达"));

        Health health = new ChainHealthIndicator(client).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void signShouldFollowPingResult() {
        when(signClient.ping()).thenReturn(true);
        assertThat(new SignHealthIndicator(signClient).health().getStatus()).isEqualTo(Status.UP);

        when(signClient.ping()).thenReturn(false);
        assertThat(new SignHealthIndicator(signClient).health().getStatus()).isEqualTo(Status.DOWN);
    }
}
