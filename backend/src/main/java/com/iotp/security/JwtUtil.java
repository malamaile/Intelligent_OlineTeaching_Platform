package com.iotp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 * <p>用于生成和解析 JSON Web Token，基于 jjwt 0.9.1 实现</p>
 */
public class JwtUtil {

    /**
     * 签名密钥（硬编码，生产环境建议从配置中心或环境变量读取）
     * 长度需满足 HMAC-SHA256 最低要求（32 字节以上）
     */
    private static final String SECRET = "IOTP_2026_SuperSecretKey_ForJWT_Token_Generation_And_Validation_32chars";

    /** Token 过期时间：30 分钟（单位：毫秒） */
    private static final long EXPIRE_MS = 30 * 60 * 1000L;

    /** 签发者标识 */
    private static final String ISSUER = "IOTP-Backend";

    // ==================== 构造方法（工具类禁止实例化） ====================

    private JwtUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ==================== Token 生成 ====================

    /**
     * 生成 JWT Token
     *
     * @param userId   用户 ID（必须）
     * @param username 用户名（必须）
     * @param role     用户角色（必须）
     * @return 签名后的 JWT 字符串
     */
    public static String generateToken(Long userId, String username, String role) {
        // 构造自定义 Claims
        Map<String, Object> claims = new HashMap<>(8);
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);

        // 签发时间 & 过期时间
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRE_MS);

        return Jwts.builder()
                .setClaims(claims)               // 自定义载荷
                .setIssuer(ISSUER)               // 签发者
                .setIssuedAt(now)                // 签发时间
                .setExpiration(expiration)       // 过期时间
                .signWith(SignatureAlgorithm.HS256, SECRET) // 签名算法 + 密钥
                .compact();
    }

    // ==================== Token 解析 ====================

    /**
     * 解析 JWT Token，返回所有 Claims
     * <p>在 Token 无效、过期或签名错误时抛出异常</p>
     *
     * @param token JWT 字符串
     * @return Claims 对象（包含所有声明）
     * @throws ExpiredJwtException     Token 已过期
     * @throws UnsupportedJwtException 不支持的 Token 格式
     * @throws MalformedJwtException   Token 格式错误
     * @throws SignatureException      签名校验失败
     * @throws IllegalArgumentException Token 为空或格式非法
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET)
                .parseClaimsJws(token)
                .getBody();
    }

    // ==================== 字段提取 ====================

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token JWT 字符串
     * @return 用户 ID（Long 类型）
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    /**
     * 从 Token 中提取用户名
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 中提取用户角色
     *
     * @param token JWT 字符串
     * @return 角色编码
     */
    public static String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    // ==================== 校验方法 ====================

    /**
     * 判断 Token 是否已过期
     *
     * @param token JWT 字符串
     * @return true-已过期；false-未过期或解析异常时返回 true（安全兜底）
     */
    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            // Token 已过期，返回 true
            return true;
        } catch (Exception e) {
            // 其他异常（格式错误、签名错误等）也视为过期，安全兜底
            return true;
        }
    }

}
