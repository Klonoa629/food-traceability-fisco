package com.foodtrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodtrace.chain.ChainReader;
import com.foodtrace.chain.ChainRoleService;
import com.foodtrace.chain.SignClient;
import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.dto.*;
import com.foodtrace.entity.SysUser;
import com.foodtrace.mapper.SysUserMapper;
import com.foodtrace.security.JwtUtil;
import com.foodtrace.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台账户服务
 *
 * <p>覆盖注册（待审批）→ 登录（JWT）→ 监管审批（Sign 开户 + 链上发角色）→ 吊销
 * （链上收角色）的完整账户生命周期，关键动作均落审计。
 *
 * @author Microft0629
 * @since 2026-09-17
 */
@Service
@RequiredArgsConstructor
public class UserService {
    /** 托管用户标识前缀，拼接登录名作为 signUserId */
    private static final String SIGN_USER_PREFIX = "ft_";

    /** 账户 Mapper */
    private final SysUserMapper userMapper;
    /** 审计服务 */
    private final OperateLogService operateLogService;
    /** 密码编码器 */
    private final PasswordEncoder passwordEncoder;
    /** JWT 工具 */
    private final JwtUtil jwtUtil;
    /** WeBASE-Sign 客户端 */
    private final SignClient signClient;
    /** 链上角色管理 */
    private final ChainRoleService chainRoleService;
    /** 链上只读查询 */
    private final ChainReader chainReader;

    /**
     * 按登录名查询账户（认证过滤器使用）
     *
     * @param username 登录名
     * @return 账户实体，不存在返回 null
     */
    public SysUser findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    /**
     * 账户注册，注册后处于待审批状态
     *
     * @param request 注册请求
     * @return 新账户 id
     * @throws BizException 用户名已存在时抛出
     */
    public Long register(RegisterRequest request) {
        if (findByUsername(request.username()) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setOrgName(request.orgName());
        user.setRole(request.role());
        user.setIsRegulator(false);
        user.setStatus(0);
        userMapper.insert(user);
        operateLogService.record(user.getId(), user.getUsername(),
                "REGISTER", user.getId(), null, "注册待审批：" + user.getOrgName());
        return user.getId();
    }

    /**
     * 账户登录，签发 JWT
     *
     * @param request 登录请求
     * @return 令牌与账户信息
     * @throws BizException 凭据错误、待审批或已吊销时抛出
     */
    public LoginResponse login(LoginRequest request) {
        SysUser user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BizException(ErrorCode.INVALID_STATE, "账户待审批，暂不能登录");
        }
        if (user.getStatus() != 1) {
            throw new BizException(ErrorCode.INVALID_STATE, "账户已吊销");
        }
        operateLogService.record(user.getId(), user.getUsername(),
                "LOGIN", null, null, "登录成功");
        return new LoginResponse(jwtUtil.issue(user), UserInfo.from(user));
    }

    /**
     * 条件查询账户列表
     *
     * @param status 账户状态（可空，空为全部）
     * @return 按创建时间倒序的账户信息列表
     */
    public List<UserInfo> list(Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getId);
        return userMapper.selectList(wrapper).stream().map(UserInfo::from).toList();
    }

    /**
     * 查询生效中的参与机构（供交接时选择下游）
     *
     * @return 生效的非监管账户列表
     */
    public List<UserInfo> listActiveOrgs() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1)
                        .eq(SysUser::getIsRegulator, false)
                        .orderByDesc(SysUser::getId))
                .stream().map(UserInfo::from).toList();
    }

    /**
     * 审批账户：Sign 开托管户 → 链上发角色 → 账户生效
     *
     * @param id       待审批账户 id
     * @param role     审批通过的合约角色 1-6
     * @param operator 执行审批的监管账户
     * @return 生效后的账户信息
     * @throws BizException 账户不存在、状态不符、开户或上链失败时抛出
     */
    public UserInfo approve(Long id, int role, LoginUser operator) {
        SysUser user = requireUser(id);
        if (user.getStatus() != 0) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅待审批账户可通过审批");
        }
        if (Boolean.TRUE.equals(user.getIsRegulator())) {
            throw new BizException(ErrorCode.INVALID_STATE, "监管账户不走审批流程");
        }
        if (operator.signUserId() == null) {
            throw new BizException(ErrorCode.INVALID_STATE, "当前监管账户未绑定签名用户");
        }
        // 1. WeBASE-Sign 开托管户，取得链上地址
        SignClient.SignUser signUser = signClient.createOrGet(SIGN_USER_PREFIX + user.getUsername());
        // 2. 以审批监管人身份在链上发放角色；已持有同角色时跳过，保证重试幂等
        String txHash = null;
        if (readChainRole(signUser.address()) != role) {
            txHash = chainRoleService.grantRole(operator.signUserId(), signUser.address(), role);
        }
        // 3. 回填链上身份，账户生效
        user.setRole(role);
        user.setChainAddress(signUser.address());
        user.setSignUserId(signUser.signUserId());
        user.setStatus(1);
        userMapper.updateById(user);
        operateLogService.record(operator.id(), operator.username(),
                "APPROVE_USER", id, txHash,
                "审批账户 " + user.getUsername() + "，发放角色 " + role);
        return UserInfo.from(user);
    }

    /**
     * 吊销账户：链上收角色（如有）→ 账户置为已吊销
     *
     * @param id       生效账户 id
     * @param operator 执行吊销的监管账户
     * @return 吊销后的账户信息
     * @throws BizException 账户不存在、状态不符或吊销自己时抛出
     */
    public UserInfo revoke(Long id, LoginUser operator) {
        if (id.equals(operator.id())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能吊销当前登录账户");
        }
        SysUser user = requireUser(id);
        if (user.getStatus() != 1) {
            throw new BizException(ErrorCode.INVALID_STATE, "仅生效账户可吊销");
        }
        // 监管账户不持有六类机构角色，仅普通机构需要链上收角色
        String txHash = null;
        if (!Boolean.TRUE.equals(user.getIsRegulator()) && user.getChainAddress() != null) {
            if (readChainRole(user.getChainAddress()) != 0) {
                txHash = chainRoleService.removeRole(operator.signUserId(), user.getChainAddress());
            }
        }
        user.setStatus(2);
        userMapper.updateById(user);
        operateLogService.record(operator.id(), operator.username(),
                "REVOKE_USER", id, txHash, "吊销账户 " + user.getUsername());
        return UserInfo.from(user);
    }

    /**
     * 查询机构当前链上角色，查询失败按链上异常处理
     *
     * @param chainAddress 机构链上地址
     * @return 合约 Role 枚举数值
     * @throws BizException 链上查询失败时抛出
     */
    private int readChainRole(String chainAddress) {
        try {
            return chainReader.roles(chainAddress);
        } catch (IllegalStateException e) {
            throw new BizException(ErrorCode.CHAIN_REJECTED, e.getMessage());
        }
    }

    /**
     * 加载账户，不存在即抛 404
     *
     * @param id 账户 id
     * @return 账户实体
     * @throws BizException 账户不存在时抛出
     */
    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "账户不存在");
        }
        return user;
    }
}
