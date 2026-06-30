package com.iotp.controller;

import com.iotp.common.Result;
import com.iotp.entity.SysConfig;
import com.iotp.mapper.SysConfigMapper;
import com.iotp.security.UserContext;
import com.iotp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * <p>提供登录、登出、密码重置、当前用户信息查询等 RESTful 接口。</p>
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": {},
 *   "timestamp": 1719200000000
 * }
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    /** Authorization 请求头前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    // ==================== 注册 ====================

    /**
     * 用户注册
     *
     * POST /auth/register
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        // 密码长度校验（读取系统配置 min/max）
        Result<String> pwdCheck = validatePasswordLength(password);
        if (pwdCheck != null) return Result.error(400, pwdCheck.getMessage());

        String account = body.get("account");
        String userName = body.get("userName");
        String role = body.get("role");
        String department = body.get("department");
        String className = body.get("className");
        String email = body.get("email");
        String phone = body.get("phone");

        Map<String, Object> data = authService.register(account, password, userName,
                role, department, className, email, phone);
        return Result.ok("注册成功，请使用账号登录", data);
    }

    // ==================== 登录 ====================

    /**
     * 用户登录
     *
     * POST /auth/login
     * Request:  { "account": "...", "password": "...", "role": "..." }
     * Response: { token, expireAt, userInfo }
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String account = body.get("account");
        String password = body.get("password");
        String role = body.get("role");

        Map<String, Object> data = authService.login(account, password, role);
        return Result.ok(data);
    }

    // ==================== 忘记密码 — 身份验证 ====================

    /**
     * 忘记密码 — 验证身份
     *
     * POST /auth/forgot-password/verify
     * Request:  { "account": "...", "role": "...", "reservedInfo": "..." }
     * Response: { resetToken }
     */
    @PostMapping("/forgot-password/verify")
    public Result<Map<String, String>> forgotPasswordVerify(@RequestBody Map<String, String> body) {
        String account = body.get("account");
        String role = body.get("role");
        String reservedInfo = body.get("reservedInfo");

        String resetToken = authService.forgotPasswordVerify(account, role, reservedInfo);

        Map<String, String> data = new HashMap<>();
        data.put("resetToken", resetToken);
        return Result.ok(data);
    }

    // ==================== 忘记密码 — 重置密码 ====================

    /**
     * 忘记密码 — 重置密码
     *
     * POST /auth/forgot-password/reset
     * Request:  { "resetToken": "...", "newPassword": "..." }
     */
    @PostMapping("/forgot-password/reset")
    public Result<String> forgotPasswordReset(@RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        Result<String> pwdCheck = validatePasswordLength(newPassword);
        if (pwdCheck != null) return pwdCheck;

        String resetToken = body.get("resetToken");
        authService.forgotPasswordReset(resetToken, newPassword);
        return Result.ok("密码重置成功，请使用新密码登录");
    }

    // ==================== 登出 ====================

    /**
     * 用户登出
     *
     * POST /auth/logout
     * Header: Authorization: Bearer {token}
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        authService.logout(token);
        return Result.ok();
    }

    // ==================== 获取当前用户信息 ====================

    /**
     * 获取当前登录用户信息
     *
     * GET /auth/current-user
     * Header: Authorization: Bearer {token}
     */
    @GetMapping("/current-user")
    public Result<Map<String, Object>> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        Map<String, Object> data = authService.getCurrentUser(token);
        return Result.ok(data);
    }

    // ==================== 修改密码 ====================

    /**
     * 修改密码
     *
     * PUT /auth/password
     * Request:  { "oldPassword": "...", "newPassword": "..." }
     */
    @PutMapping("/password")
    public Result<String> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        // 从 UserContext 获取当前登录用户 ID（由 LoginInterceptor 设置）
        Long userId = UserContext.getUserId();

        authService.changePassword(userId, oldPassword, newPassword);
        return Result.ok("密码修改成功");
    }

    // ==================== 私有工具方法 ====================

    /**
     * 从 Authorization 请求头中提取 Bearer Token
     *
     * @param authHeader Authorization 请求头值
     * @return 去掉 "Bearer " 前缀后的 Token 字符串；若格式无效则返回 null
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    /** 根据系统配置校验密码长度，通过返回 null */
    private Result<String> validatePasswordLength(String password) {
        if (password == null) return null;
        int minLen = getConfigInt("password_min_length", 6);
        int maxLen = getConfigInt("password_max_length", 20);
        if (password.length() < minLen) {
            return Result.error(400, "密码长度不能少于 " + minLen + " 位");
        }
        if (password.length() > maxLen) {
            return Result.error(400, "密码长度不能超过 " + maxLen + " 位");
        }
        return null;
    }

    private int getConfigInt(String key, int defaultValue) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysConfig> w =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            w.eq(SysConfig::getConfigKey, key);
            SysConfig cfg = sysConfigMapper.selectOne(w);
            return (cfg != null && cfg.getConfigValue() != null)
                    ? Integer.parseInt(cfg.getConfigValue()) : defaultValue;
        } catch (Exception e) { return defaultValue; }
    }
}
