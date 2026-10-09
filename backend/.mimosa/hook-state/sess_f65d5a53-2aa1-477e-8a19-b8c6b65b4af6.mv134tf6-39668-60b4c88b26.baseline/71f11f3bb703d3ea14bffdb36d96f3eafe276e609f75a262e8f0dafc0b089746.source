package com.foodtrace.controller;

import com.foodtrace.chain.ProductCache;
import com.foodtrace.config.ContractProperties;
import com.foodtrace.config.SecurityConfig;
import com.foodtrace.dto.LoginResponse;
import com.foodtrace.dto.ProductVO;
import com.foodtrace.dto.UserInfo;
import com.foodtrace.entity.SysUser;
import com.foodtrace.security.JwtAuthenticationFilter;
import com.foodtrace.security.JwtUtil;
import com.foodtrace.security.LoginUser;
import com.foodtrace.security.RateLimitService;
import com.foodtrace.service.OperateLogService;
import com.foodtrace.service.ProductService;
import com.foodtrace.service.UserService;
import org.fisco.bcos.sdk.v3.client.Client;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 鉴权链集成测试：真实过滤器与安全配置下的 401/403/200 矩阵
 *
 * <p>加载真实的 SecurityConfig、JWT 过滤器与异常处理，仅 mock 业务服务，
 * 不依赖数据库与链环境。
 *
 * @author Microft0629
 * @since 2026-09-30
 */
@WebMvcTest
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class,
        ContractProperties.class, RateLimitService.class})
@TestPropertySource(properties = {
        "foodtrace.jwt.secret=test-secret-0123456789abcdef0123456789abcdef",
        "foodtrace.jwt.ttl-minutes=120"})
class SecurityIntegrationTest {
    /** MVC 测试入口 */
    @Autowired
    private MockMvc mockMvc;
    /** JWT 工具（真实签发） */
    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private OperateLogService operateLogService;
    @MockitoBean
    private Client client;
    @MockitoBean
    private ProductCache productCache;

    /**
     * 构造指定身份与状态的账户实体
     */
    private SysUser account(long id, String username, boolean regulator, int role, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setOrgName(username + "机构");
        user.setIsRegulator(regulator);
        user.setRole(role);
        user.setStatus(status);
        user.setSignUserId("ft_" + username);
        user.setChainAddress("0x" + String.format("%040x", id));
        return user;
    }

    /**
     * 让过滤器查库时返回指定账户并签发其令牌
     */
    private String tokenOf(SysUser user) {
        when(userService.findByUsername(user.getUsername())).thenReturn(user);
        return jwtUtil.issue(user);
    }

    @Test
    void anonymousRequestShouldGet401() throws Exception {
        mockMvc.perform(get("/api/orgs")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenShouldGet401() throws Exception {
        mockMvc.perform(get("/api/orgs").header("Authorization", "Bearer garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendingAccountTokenShouldGet401() throws Exception {
        String token = tokenOf(account(2L, "farm_x", false, 1, 0));
        mockMvc.perform(get("/api/orgs").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedAccountTokenShouldGet401() throws Exception {
        String token = tokenOf(account(2L, "farm_x", false, 1, 2));
        mockMvc.perform(get("/api/orgs").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordinaryUserOnAdminApiShouldGet403() throws Exception {
        String token = tokenOf(account(2L, "farm_x", false, 1, 1));
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void regulatorOnAdminApiShouldGet200() throws Exception {
        String token = tokenOf(account(1L, "regulator", true, 0, 1));
        when(userService.list(isNull())).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void loginAndRegisterShouldBeAnonymous() throws Exception {
        SysUser farm = account(2L, "farm_x", false, 1, 1);
        when(userService.login(any())).thenReturn(new LoginResponse("t", UserInfo.from(farm)));
        when(userService.register(any())).thenReturn(9L);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"farm_x\",\"password\":\"pass123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("t"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"farm_y\",\"password\":\"pass123456\","
                                + "\"orgName\":\"farm\",\"role\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(9));
    }

    @Test
    void blankLoginParamShouldGet400Code() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminActionShouldInjectPrincipalIdentity() throws Exception {
        String token = tokenOf(account(1L, "regulator", true, 0, 1));
        SysUser target = account(2L, "farm_x", false, 1, 1);
        when(userService.approve(anyLong(), anyInt(), any())).thenReturn(UserInfo.from(target));

        mockMvc.perform(post("/api/admin/users/2/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 校验 @AuthenticationPrincipal 注入的身份字段完整
        ArgumentCaptor<LoginUser> captor = ArgumentCaptor.forClass(LoginUser.class);
        verify(userService).approve(eq(2L), eq(1), captor.capture());
        LoginUser operator = captor.getValue();
        assertThat(operator.id()).isEqualTo(1L);
        assertThat(operator.regulator()).isTrue();
        assertThat(operator.role()).isZero();
        assertThat(operator.signUserId()).isEqualTo("ft_regulator");
        assertThat(operator.chainAddress()).isEqualTo("0x" + String.format("%040x", 1L));
    }

    @Test
    void unknownPathShouldGet404Code() throws Exception {
        String token = tokenOf(account(1L, "regulator", true, 0, 1));
        mockMvc.perform(get("/api/bogus-xyz").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("接口不存在"));
    }

    @Test
    void publicTraceShouldRateLimitPerIp() throws Exception {
        when(productCache.getByBatch(anyString())).thenReturn(new ProductVO(
                1L, "草莓", "B1", "0xf", "0xf", 0, false, List.of()));
        // 前 30 次放行
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/public/trace").param("batchNo", "B1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
        // 第 31 次触发限流
        mockMvc.perform(get("/api/public/trace").param("batchNo", "B1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后再试"));
    }
}
