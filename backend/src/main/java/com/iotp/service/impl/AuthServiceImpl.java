package com.iotp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iotp.common.BusinessException;
import com.iotp.entity.*;
import com.iotp.mapper.*;
import com.iotp.security.JwtUtil;
import com.iotp.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    // ==================== 账号状态常量（与DB一致：1-启用, 0-禁用） ====================
    private static final Integer STATUS_ACTIVE = 1;
    private static final Integer STATUS_FROZEN = 0;

    // ==================== 密码重置常量 ====================
    /** 重置令牌过期时间（分钟） */
    private static final int RESET_TOKEN_EXPIRE_MINUTES = 5;

    /** 登录失败最大次数（超出后锁定） */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    /** 锁定时长（分钟） */
    private static final int LOCK_DURATION_MINUTES = 30;

    // ==================== 注入 Mapper ====================

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysPasswordResetMapper sysPasswordResetMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysLoginLogMapper sysLoginLogMapper;

    @Autowired
    private SysDepartmentMapper sysDepartmentMapper;

    @Autowired
    private SysClassMapper sysClassMapper;

    // ==================== 登录 ====================

    @Override
    public Map<String, Object> login(String account, String password, String role) {
        // 1. 按用户名查找用户
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", account);
        SysUser user = sysUserMapper.selectOne(queryWrapper);

        // 2. 检查账号是否存在
        if (user == null) {
            log.warn("登录失败：账号 {} 不存在", account);
            throw new BusinessException(401, "账号不存在");
        }

        // 3. 检查账号是否被锁定（lock_until_time > 当前时间）
        if (user.getLockUntilTime() != null && user.getLockUntilTime().isAfter(LocalDateTime.now())) {
            log.warn("登录失败：账号 {} 已被锁定至 {}", account, user.getLockUntilTime());
            throw new BusinessException(423, "账号已被锁定，请稍后再试");
        }
        // 4. 检查账号是否被冻结（status=0）
        if (STATUS_FROZEN.equals(user.getStatus())) {
            log.warn("登录失败：账号 {} 已被冻结", account);
            throw new BusinessException(403, "账号已被冻结，请联系管理员");
        }

        // 4. 校验角色是否匹配
        SysRole sysRole = sysRoleMapper.selectById(user.getRoleId());
        if (sysRole != null && role != null && !role.isEmpty()) {
            if (!role.equals(sysRole.getRoleCode())) {
                log.warn("登录失败：账号 {} 的角色不匹配（请求={}，实际={}）",
                        account, role, sysRole.getRoleCode());
                // 不暴露具体角色信息，统一提示
                throw new BusinessException(401, "账号或密码错误");
            }
        }

        // 5. 验证密码
        if (!password.equals(user.getPassword())) {
            // 密码错误：递增失败次数
            int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
            user.setLoginFailCount(failCount);

            if (failCount >= MAX_LOGIN_FAIL_COUNT) {
                // 达到最大失败次数，锁定账号（设置锁定时长，不改status）
                user.setLockUntilTime(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                log.warn("账号 {} 因密码错误超限已被锁定至 {}", account, user.getLockUntilTime());
            }
            sysUserMapper.updateById(user);

            // 记录失败登录日志
            recordLoginLog(user.getId(), "FAIL", "密码错误");

            throw new BusinessException(401, "账号或密码错误");
        }

        // 6. 登录成功：获取角色名称
        String roleName = (sysRole != null) ? sysRole.getRoleName() : "";

        // 7. 生成 JWT Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), roleName);
        Date expireAt = JwtUtil.parseToken(token).getExpiration();

        // 8. 更新用户信息（重置失败次数、清除锁定、更新登录时间）
        user.setLoginFailCount(0);
        user.setLockUntilTime(null);
        user.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(user);

        // 9. 记录成功登录日志
        recordLoginLog(user.getId(), "SUCCESS", null);

        // 10. 查询部门和班级名称
        String departmentName = getDepartmentName(user.getDepartmentId());
        String className = getClassName(user.getClassId());

        // 11. 构建用户信息
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("account", user.getUsername());
        userInfo.put("userName", user.getRealName());
        userInfo.put("role", roleName);
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("department", departmentName);
        userInfo.put("className", className);

        // 12. 构建响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("expireAt", expireAt.getTime());
        result.put("userInfo", userInfo);

        log.info("用户 {} 登录成功（角色：{}）", account, roleName);
        return result;
    }

    // ==================== 忘记密码 — 身份验证 ====================

    @Override
    public String forgotPasswordVerify(String account, String role, String reservedInfo) {
        // 1. 按用户名查找用户
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", account);
        SysUser user = sysUserMapper.selectOne(queryWrapper);

        if (user == null) {
            throw new BusinessException(404, "账号不存在");
        }

        // 2. 校验角色
        if (role != null && !role.isEmpty()) {
            SysRole sysRole = sysRoleMapper.selectById(user.getRoleId());
            if (sysRole != null && !role.equals(sysRole.getRoleCode())) {
                throw new BusinessException(400, "角色信息不匹配");
            }
        }

        // 3. 验证预留信息（匹配 realName / email / phone 中的任意一个）
        boolean infoMatched = false;
        if (reservedInfo != null && !reservedInfo.isEmpty()) {
            if (reservedInfo.equals(user.getRealName())
                    || reservedInfo.equals(user.getEmail())
                    || reservedInfo.equals(user.getPhone())) {
                infoMatched = true;
            }
        }

        if (!infoMatched) {
            throw new BusinessException(400, "预留信息验证失败");
        }

        // 4. 生成重置令牌
        String resetToken = UUID.randomUUID().toString().replace("-", "");

        SysPasswordReset resetRecord = new SysPasswordReset();
        resetRecord.setUserId(user.getId());
        resetRecord.setResetToken(resetToken);
        resetRecord.setExpireTime(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRE_MINUTES));
        resetRecord.setIsUsed(0);

        sysPasswordResetMapper.insert(resetRecord);

        log.info("用户 {} 已生成密码重置令牌", account);
        return resetToken;
    }

    // ==================== 忘记密码 — 重置密码 ====================

    @Override
    public void forgotPasswordReset(String resetToken, String newPassword) {
        // 1. 查询重置记录
        QueryWrapper<SysPasswordReset> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("reset_token", resetToken);
        SysPasswordReset resetRecord = sysPasswordResetMapper.selectOne(queryWrapper);

        if (resetRecord == null) {
            throw new BusinessException(400, "重置令牌无效");
        }

        // 2. 检查是否已使用
        if (Integer.valueOf(1).equals(resetRecord.getIsUsed())) {
            throw new BusinessException(400, "重置令牌已使用");
        }

        // 3. 检查是否过期
        if (resetRecord.getExpireTime() != null
                && resetRecord.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "重置令牌已过期，请重新验证身份");
        }

        // 4. 更新用户密码
        SysUser user = sysUserMapper.selectById(resetRecord.getUserId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        user.setPassword(newPassword);
        // 重置成功后解锁账号（清除锁定时间）
        if (user.getLockUntilTime() != null) {
            user.setLockUntilTime(null);
        }
        user.setLoginFailCount(0);
        sysUserMapper.updateById(user);

        // 5. 标记令牌为已使用
        resetRecord.setIsUsed(1);
        sysPasswordResetMapper.updateById(resetRecord);

        log.info("用户 {} 密码重置成功", user.getUsername());
    }

    // ==================== 登出 ====================

    @Override
    public void logout(String token) {
        // 1. 解析 Token 获取用户 ID
        Long userId;
        try {
            userId = JwtUtil.getUserId(token);
        } catch (Exception e) {
            log.warn("登出时 Token 解析失败：{}", e.getMessage());
            throw new BusinessException(401, "无效的 Token");
        }

        // 2. 更新最近一条登录日志的登出时间
        QueryWrapper<SysLoginLog> logQuery = new QueryWrapper<>();
        logQuery.eq("user_id", userId)
                .eq("login_result", "SUCCESS")
                .orderByDesc("login_time")
                .last("LIMIT 1");
        SysLoginLog loginLog = sysLoginLogMapper.selectOne(logQuery);

        if (loginLog != null && loginLog.getLogoutTime() == null) {
            loginLog.setLogoutTime(LocalDateTime.now());
            sysLoginLogMapper.updateById(loginLog);
            log.info("用户 ID {} 已登出", userId);
        }
    }

    // ==================== 获取当前用户信息 ====================

    @Override
    public Map<String, Object> getCurrentUser(String token) {
        // 1. 解析 Token
        Long userId;
        String username;
        String role;

        try {
            userId = JwtUtil.getUserId(token);
            username = JwtUtil.getUsername(token);
            role = JwtUtil.getRole(token);
        } catch (Exception e) {
            log.warn("获取当前用户信息时 Token 解析失败：{}", e.getMessage());
            throw new BusinessException(401, "无效的 Token");
        }

        // 2. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 3. 查询关联信息
        String departmentName = getDepartmentName(user.getDepartmentId());
        String className = getClassName(user.getClassId());
        String roleName = role;

        SysRole sysRole = sysRoleMapper.selectById(user.getRoleId());
        if (sysRole != null) {
            roleName = sysRole.getRoleName();
        }

        // 4. 构建完整的用户信息
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("account", user.getUsername());
        userInfo.put("userName", user.getRealName());
        userInfo.put("role", roleName);
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("email", user.getEmail());
        userInfo.put("phone", user.getPhone());
        userInfo.put("department", departmentName);
        userInfo.put("className", className);
        userInfo.put("status", user.getStatus());
        userInfo.put("lastLoginTime", user.getLastLoginTime());

        return userInfo;
    }

    // ==================== 修改密码 ====================

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        // 1. 查找用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 2. 验证旧密码
        if (!oldPassword.equals(user.getPassword())) {
            throw new BusinessException(400, "旧密码错误");
        }

        // 3. 更新密码
        user.setPassword(newPassword);
        sysUserMapper.updateById(user);

        log.info("用户 ID {} 密码修改成功", userId);
    }

    // ==================== 私有工具方法 ====================

    /**
     * 记录登录日志
     */
    private void recordLoginLog(Long userId, String loginResult, String failReason) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUserId(userId);
        loginLog.setLoginTime(LocalDateTime.now());
        loginLog.setLoginResult(loginResult);
        loginLog.setFailReason(failReason);
        loginLog.setCreateTime(LocalDateTime.now());
        sysLoginLogMapper.insert(loginLog);
    }

    /**
     * 根据部门 ID 查询部门名称
     */
    private String getDepartmentName(Long departmentId) {
        if (departmentId == null) {
            return "";
        }
        SysDepartment dept = sysDepartmentMapper.selectById(departmentId);
        return dept != null ? dept.getDeptName() : "";
    }

    /**
     * 根据班级 ID 查询班级名称
     */
    private String getClassName(Long classId) {
        if (classId == null) {
            return "";
        }
        SysClass cls = sysClassMapper.selectById(classId);
        return cls != null ? cls.getClassName() : "";
    }

}
