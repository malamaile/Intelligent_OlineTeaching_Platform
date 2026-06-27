package com.iotp.service;

import java.util.Map;

/**
 * 认证服务接口
 * <p>提供登录、登出、密码重置、当前用户信息查询等认证相关功能。</p>
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param account  用户名
     * @param password 密码（明文）
     * @param role     角色编码（用于校验用户角色是否匹配）
     * @return 包含 token、expireAt、userInfo 的登录结果
     */
    Map<String, Object> login(String account, String password, String role);

    /**
     * 忘记密码 — 身份验证
     *
     * @param account      用户名
     * @param role         角色编码
     * @param reservedInfo 预留信息（匹配 realName / email / phone）
     * @return 重置令牌 resetToken
     */
    String forgotPasswordVerify(String account, String role, String reservedInfo);

    /**
     * 忘记密码 — 重置密码
     *
     * @param resetToken  重置令牌
     * @param newPassword 新密码（明文）
     */
    void forgotPasswordReset(String resetToken, String newPassword);

    /**
     * 用户登出
     *
     * @param token JWT Token
     */
    void logout(String token);

    /**
     * 获取当前登录用户信息
     *
     * @param token JWT Token
     * @return 包含用户详细信息的 Map
     */
    Map<String, Object> getCurrentUser(String token);

    /**
     * 修改密码
     *
     * @param userId      用户 ID（从 UserContext 获取）
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 用户注册
     *
     * @param account   用户名（学号/工号）
     * @param password  密码（明文）
     * @param userName  真实姓名
     * @param roleCode   角色编码：STUDENT / TEACHER
     * @param department 院系名称
     * @param className  班级名称（学生必填）
     * @param email     邮箱（可选）
     * @param phone     手机号（可选）
     * @return 注册成功的用户信息
     */
    Map<String, Object> register(String account, String password, String userName,
                                  String roleCode, String department, String className,
                                  String email, String phone);
}
