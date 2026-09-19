package com.foodtrace.service;

import com.foodtrace.chain.ChainProductService;
import com.foodtrace.chain.ChainReader;
import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.dto.*;
import com.foodtrace.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 产品业务服务单元测试：角色前置校验与注册/召回流程
 *
 * @author Microft0629
 * @since 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ChainProductService chainProductService;
    @Mock
    private ChainReader chainReader;
    @Mock
    private OperateLogService operateLogService;

    private ProductService productService;

    private final LoginUser farm =
            new LoginUser(2L, "farm_a", false, 1, "ft_farm_a", "0xfarm");
    private final LoginUser inspector =
            new LoginUser(3L, "inspector_b", false, 3, "ft_inspector_b", "0xinsp");
    private final LoginUser regulator =
            new LoginUser(1L, "regulator", true, 0, "regulator_001", "0xabc");

    /**
     * 构造被测服务
     */
    @BeforeEach
    void setUp() {
        productService = new ProductService(chainProductService, chainReader, operateLogService);
    }

    /**
     * 构造链上产品视图
     */
    private ProductVO product(long id, int stage) {
        return new ProductVO(id, "阳光草莓", "B1", "0xfarm", "0xfarm", stage, false, List.of());
    }

    @Test
    void registerShouldRejectNonFarmRole() {
        assertThatThrownBy(() -> productService.register(
                new RegisterProductRequest("草莓", "B1", "种植", "云南", null), inspector))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void registerShouldReturnOnChainProductAndAudit() {
        when(chainProductService.registerProduct(eq("ft_farm_a"), anyString(), anyString(),
                anyString(), anyString(), anyString())).thenReturn("0xtx");
        when(chainReader.productByBatch("B1")).thenReturn(product(1, 0));

        ProductVO result = productService.register(
                new RegisterProductRequest("阳光草莓", "B1", "首批种植", "云南昆明", null), farm);

        assertThat(result.id()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("阳光草莓");
        verify(operateLogService).record(eq(2L), eq("farm_a"),
                eq("REGISTER_PRODUCT"), eq(1L), eq("0xtx"), anyString());
    }

    @Test
    void addRecordShouldRejectStageRoleMismatch() {
        // 基地角色只能写种植环节（0），写运输环节（3）应被拒绝
        assertThatThrownBy(() -> productService.addRecord(1,
                new AddRecordRequest(3, "运输中", "昆明", null), farm))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void addRecordShouldRejectInspector() {
        assertThatThrownBy(() -> productService.addRecord(1,
                new AddRecordRequest(2, "质检描述", "昆明", null), inspector))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void inspectShouldRejectNonInspectorRole() {
        assertThatThrownBy(() -> productService.inspect(1,
                new InspectRequest(true, null), farm))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void recallShouldRejectNonRegulator() {
        assertThatThrownBy(() -> productService.recall(1,
                new RecallRequest("农残超标", null), farm))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void getShouldRejectUnknownProduct() {
        when(chainReader.productCount()).thenReturn(2L);

        assertThatThrownBy(() -> productService.get(3))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void listShouldReturnNewestFirst() {
        when(chainReader.productCount()).thenReturn(2L);
        when(chainReader.product(1L)).thenReturn(product(1, 0));
        when(chainReader.product(2L)).thenReturn(product(2, 2));

        assertThat(productService.list()).extracting(ProductVO::id)
                .containsExactly(2L, 1L);
    }
}
