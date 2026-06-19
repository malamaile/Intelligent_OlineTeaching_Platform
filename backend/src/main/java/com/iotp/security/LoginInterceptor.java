package com.iotp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iotp.common.Result;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器
 * <p>拦截所有需要登录认证的请求，从 Authorization 请求头中解析 JWT，
 * 校验通过后将用户信息设置到 Request 属性中，供后续 Controller 通过 UserContext 获取。</p>
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginInterceptor.class);

    /** Authorization 请求头名称 */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer Token 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** JSON 内容类型 */
    private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";

    /** 用于序列化 JSON 响应 */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 前置拦截 ====================

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 1. 放行 OPTIONS 请求（CORS 预检请求无需校验 Token）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 从请求头获取 Token
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);

        // 校验请求头是否存在且以 "Bearer " 开头
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("请求未携带有效的 Authorization 请求头 | URI: {}", request.getRequestURI());
            writeUnauthorizedResponse(response, "未登录，请先登录");
            return false;
        }

        // 3. 提取 Token（去掉 "Bearer " 前缀）
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isEmpty()) {
            log.warn("Authorization 请求头中 Token 为空 | URI: {}", request.getRequestURI());
            writeUnauthorizedResponse(response, "Token 不能为空");
            return false;
        }

        // 4. 解析 Token 并设置用户上下文到 Request 属性
        try {
            Long userId = JwtUtil.getUserId(token);
            String username = JwtUtil.getUsername(token);
            String role = JwtUtil.getRole(token);

            // 将用户信息存入 Request 属性，供后续处理器和 UserContext 使用
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("role", role);

            // 同时设置到 UserContext（ThreadLocal），方便在非 Controller 层获取
            UserContext.setUserId(userId);
            UserContext.setUsername(username);
            UserContext.setRole(role);

            return true;

        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期 | URI: {}", request.getRequestURI());
            writeUnauthorizedResponse(response, "Token 已过期，请重新登录");
            return false;
        } catch (SignatureException e) {
            log.warn("Token 签名校验失败 | URI: {}", request.getRequestURI());
            writeUnauthorizedResponse(response, "Token 签名无效");
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Token 格式错误 | URI: {}", request.getRequestURI());
            writeUnauthorizedResponse(response, "Token 格式错误");
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的 Token 格式 | URI: {}", request.getRequestURI());
            writeUnauthorizedResponse(response, "不支持的 Token 格式");
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Token 为空或参数非法 | URI: {}", request.getRequestURI());
            writeUnauthorizedResponse(response, "Token 参数非法");
            return false;
        } catch (Exception e) {
            log.error("Token 解析时发生未知异常 | URI: {}", request.getRequestURI(), e);
            writeUnauthorizedResponse(response, "Token 校验失败");
            return false;
        }
    }

    // ==================== 请求完成后清理 ====================

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {
        // 请求完成后清除 ThreadLocal，防止内存泄漏
        UserContext.clear();
    }

    // ==================== 私有工具方法 ====================

    /**
     * 向客户端写入 401 未授权 JSON 响应
     *
     * @param response HTTP 响应对象
     * @param message  提示消息
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(CONTENT_TYPE_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (PrintWriter writer = response.getWriter()) {
            // 使用统一响应类的 unauthorized 方法
            Result<?> result = Result.unauthorized(message);
            String json = objectMapper.writeValueAsString(result);
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            log.error("写入 401 响应时发生 IO 异常", e);
        }
    }

}
