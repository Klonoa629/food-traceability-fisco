package com.foodtrace.service;

import com.foodtrace.chain.ChainReader;
import com.foodtrace.chain.ChainRoleService;
import com.foodtrace.chain.SignClient;
import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.dto.LoginRequest;
import com.foodtrace.dto.ProductVO;
import com.foodtrace.dto.RegisterRequest;
import com.foodtrace.dto.UserInfo;
import com.foodtrace.entity.SysUser;
import com.foodtrace.mapper.SysUserMapper;
import com.foodtrace.security.JwtUtil;
import com.foodtrace.security.LoginUser;
import com.foodtrace.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;/**
 * 账户服务单元测试：登录状态校验与审批/吊销流程
 *
 * @author Microft0629
 * @since 2026-09-17
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OperateLogService operateLogService;
    @Mock
    private SignClient signClient;
    @Mock
    private ChainRoleService chainRoleService;
    @Mock
    private ChainReader chainReader;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ProductService productService;
    @Mock
    private RateLimitService rateLimitService;

    private UserService userService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final LoginUser regulator =
            new LoginUser(1L, "regulator", true, 0, "regulator_001", "0xabc");

    /**
     * 用真实 BCrypt 编码器与各 mock 依赖构造被测服务
     */
    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, operateLogService, passwordEncoder,
                jwtUtil, signClient, chainRoleService, chainReader, productService,
                rateLimitService);
    }

    /**
     * 构造指定状态的账户实体
     */
    private SysUser user(int status) {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("farm_a");
        user.setPasswordHash(passwordEncoder.encode("pass123"));
        user.setOrgName("示范农场");
        user.setRole(1);
        user.setIsRegulator(false);
        user.setStatus(status);
        return user;
    }

    @Test
    void loginShouldRejectPendingAccount() {
        SysUser pending = user(0);
        when(userMapper.selectOne(any())).thenReturn(pending);

        assertThatThrownBy(() -> userService.login(new LoginRequest("farm_a", "pass123")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_STATE.getCode());
    }

    @Test
    void loginShouldRejectWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(user(1));

        assertThatThrownBy(() -> userService.login(new LoginRequest("farm_a", "wrong")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        verify(operateLogService).record(eq(2L), eq("farm_a"),
                eq("LOGIN_FAILED"), isNull(), isNull(), eq("用户名或密码错误"));
        verify(rateLimitService).recordLoginFailure("farm_a");
    }

    @Test
    void loginShouldRejectWhenRateLimited() {
        doThrow(new BizException(ErrorCode.TOO_MANY_REQUESTS))
                .when(rateLimitService).checkLoginBlocked("farm_a");

        assertThatThrownBy(() -> userService.login(new LoginRequest("farm_a", "any")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode());
        // 被锁定时不应再查库
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    void registerShouldRejectDuplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(user(1));

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("farm_a", "pass123", "另一个农场", 1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_ERROR.getCode());
    }

    @Test
    void approveShouldCreateSignUserGrantRoleAndActivate() {
        SysUser pending = user(0);
        when(userMapper.selectById(2L)).thenReturn(pending);
        when(signClient.createOrGet("ft_farm_a"))
                .thenReturn(new SignClient.SignUser("ft_farm_a", "0xfarm"));
        when(chainRoleService.grantRole("regulator_001", "0xfarm", 1)).thenReturn("0xtx");

        UserInfo result = userService.approve(2L, 1, regulator);

        assertThat(result.status()).isEqualTo(1);
        assertThat(result.chainAddress()).isEqualTo("0xfarm");
        assertThat(result.signUserId()).isEqualTo("ft_farm_a");
        verify(operateLogService).record(eq(1L), eq("regulator"),
                eq("APPROVE_USER"), eq(2L), eq("0xtx"), anyString());
    }

    @Test
    void approveShouldRejectNonPendingAccount() {
        when(userMapper.selectById(2L)).thenReturn(user(2));

        assertThatThrownBy(() -> userService.approve(2L, 1, regulator))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_STATE.getCode());
        verify(operateLogService).record(eq(1L), eq("regulator"),
                eq("APPROVE_USER_FAILED"), eq(2L), isNull(), anyString());
    }

    @Test
    void revokeShouldRejectSelfRevoke() {
        assertThatThrownBy(() -> userService.revoke(1L, regulator))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_ERROR.getCode());
    }

    @Test
    void revokeShouldRejectWhenHolderHasInFlightProducts() {
        SysUser active = user(1);
        active.setChainAddress("0xfarm");
        when(userMapper.selectById(2L)).thenReturn(active);
        when(productService.findInFlight("0xfarm"))
                .thenReturn(List.of(new ProductVO(3, "阳光草莓", "B1", "0xfarm",
                        "0xfarm", 0, false, List.of())));

        assertThatThrownBy(() -> userService.revoke(2L, regulator))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_STATE.getCode());
        // 链上收角色不应被触发
        verify(chainRoleService, never()).removeRole(anyString(), anyString());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void revokeShouldRemoveChainRoleThenMarkRevoked() {
        SysUser active = user(1);
        active.setChainAddress("0xfarm");
        active.setSignUserId("ft_farm_a");
        when(userMapper.selectById(2L)).thenReturn(active);
        when(productService.findInFlight("0xfarm")).thenReturn(List.of());
        when(chainReader.roles("0xfarm")).thenReturn(1);
        when(chainRoleService.removeRole("regulator_001", "0xfarm")).thenReturn("0xtx2");

        UserInfo result = userService.revoke(2L, regulator);

        assertThat(result.status()).isEqualTo(2);
        verify(chainRoleService).removeRole("regulator_001", "0xfarm");
    }
}
