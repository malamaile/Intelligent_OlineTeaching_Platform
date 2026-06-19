package com.iotp.controller;

import com.iotp.common.Result;
import com.iotp.security.UserContext;
import com.iotp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * <p>提供登录、登出、密码重置、当前用户信息查询等 RESTful 接口。</p>
 *
 * @author 杨雨洁
 * @since 2026-06-18
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /** Authorization 请求头前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

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
        String resetToken = body.get("resetToken");
        String newPassword = body.get("newPassword");

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
}
