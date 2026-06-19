package com.iotp.security;

/**
 * 当前登录用户上下文（ThreadLocal）
 * <p>在 LoginInterceptor 中从解析后的 JWT 获取用户信息，
 * 设置到 ThreadLocal 中，业务层可通过此类的静态方法随时获取当前登录用户信息，
 * 避免在方法参数中层层传递。</p>
 *
 * <p><b>注意：</b>请求完成后务必调用 {@link #clear()} 方法清理 ThreadLocal，
 * 防止内存泄漏。已在 LoginInterceptor 的 afterCompletion 中自动清理。</p>
 *
 * @author 杨雨洁
 * @since 2026-06-18
 */
public class UserContext {

    /** 当前登录用户 ID */
    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();

    /** 当前登录用户名 */
    private static final ThreadLocal<String> usernameHolder = new ThreadLocal<>();

    /** 当前登录用户角色 */
    private static final ThreadLocal<String> roleHolder = new ThreadLocal<>();

    // ==================== 构造函数（工具类禁止实例化） ====================

    private UserContext() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ==================== UserId ====================

    public static void setUserId(Long userId) {
        userIdHolder.set(userId);
    }

    public static Long getUserId() {
        return userIdHolder.get();
    }

    // ==================== Username ====================

    public static void setUsername(String username) {
        usernameHolder.set(username);
    }

    public static String getUsername() {
        return usernameHolder.get();
    }

    // ==================== Role ====================

    public static void setRole(String role) {
        roleHolder.set(role);
    }

    public static String getRole() {
        return roleHolder.get();
    }

    // ==================== 清理 ====================

    /**
     * 清除当前线程的所有用户上下文
     * <p>在请求结束时必须调用，防止 ThreadLocal 内存泄漏。</p>
     */
    public static void clear() {
        userIdHolder.remove();
        usernameHolder.remove();
        roleHolder.remove();
    }

}
