package com.iotp.aspect;

import com.iotp.entity.SysOperationLog;
import com.iotp.mapper.SysOperationLogMapper;
import com.iotp.security.UserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理员操作日志 AOP 切面
 * 拦截 AdminController 所有方法，自动记录操作日志到 sys_operation_log 表
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    @Autowired
    private SysOperationLogMapper operationLogMapper;

    /** 操作模块映射：根据 URL 路径推断 */
    @Around("execution(* com.iotp.controller.AdminController.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        String url = getRequestUrl();

        // 跳过内部工具方法和监控查询（选择性记录）
        if ("parseDepartmentId".equals(methodName)) {
            return joinPoint.proceed();
        }

        SysOperationLog opLog = new SysOperationLog();
        opLog.setRequestMethod(getRequestMethod());
        opLog.setRequestUrl(url);
        opLog.setModule(inferModule(methodName, url));
        opLog.setOperation(inferOperation(methodName));
        opLog.setRequestParams(safeTruncate(getRequestParams(joinPoint.getArgs()), 500));

        // 操作人
        try {
            opLog.setOperatorId(UserContext.getUserId());
            opLog.setOperatorName(UserContext.getUsername());
        } catch (Exception e) {
            opLog.setOperatorName("系统");
        }

        // 操作 IP
        opLog.setIp(getClientIp());

        Object result = null;
        try {
            result = joinPoint.proceed();
            opLog.setResultStatus(1);
        } catch (Exception e) {
            opLog.setResultStatus(0);
            opLog.setErrorMsg(safeTruncate(e.getMessage(), 500));
            throw e;
        } finally {
            opLog.setDurationMs(System.currentTimeMillis() - start);
            opLog.setDescription(buildDescription(methodName, joinPoint.getArgs()));
            try {
                operationLogMapper.insert(opLog);
            } catch (Exception e) {
                log.warn("操作日志写入失败: {}", e.getMessage());
            }
        }
        return result;
    }

    // ==================== 辅助方法 ====================

    /** 推断操作模块 */
    private String inferModule(String methodName, String url) {
        if (url == null) return "未知";
        if (url.contains("/users")) return "用户管理";
        if (url.contains("/audit/courses")) return "课程审核";
        if (url.contains("/audit/tasks")) return "任务审核";
        if (url.contains("/audit/resources")) return "资源审核";
        if (url.contains("/announcements")) return "公告管理";
        if (url.contains("/settings")) return "系统设置";
        if (url.contains("/analytics")) return "学情分析";
        if (url.contains("/monitor")) return "系统监控";
        return "其他";
    }

    /** 推断操作类型 */
    private String inferOperation(String methodName) {
        if (methodName.startsWith("get")) return "查询";
        if (methodName.startsWith("create") || methodName.startsWith("import")) return "新增";
        if (methodName.startsWith("update")) return "修改";
        if (methodName.startsWith("delete")) return "删除";
        if (methodName.startsWith("reset")) return "重置";
        if (methodName.startsWith("audit")) return "审核";
        if (methodName.startsWith("export")) return "导出";
        return "其他";
    }

    /** 构建操作描述 */
    private String buildDescription(String methodName, Object[] args) {
        if (args == null || args.length == 0) return methodName;
        // 提取第一个有意义参数作为描述
        for (Object arg : args) {
            if (arg instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) arg;
                if (map.containsKey("title")) return "公告: " + map.get("title");
                if (map.containsKey("account")) return "用户: " + map.get("account");
                if (map.containsKey("userName")) return "编辑用户: " + map.get("userName");
                if (map.containsKey("status")) return "变更状态: " + map.get("status");
                if (map.containsKey("action")) return "审核动作: " + map.get("action");
                return methodName + " " + map.keySet();
            }
            if (arg instanceof Long) return methodName + " ID=" + arg;
            if (arg instanceof String) return safeTruncate((String) arg, 100);
        }
        return methodName;
    }

    private String getRequestMethod() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getMethod() : "UNKNOWN";
        } catch (Exception e) { return "UNKNOWN"; }
    }

    private String getRequestUrl() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                return req.getMethod() + " " + req.getRequestURI();
            }
        } catch (Exception e) { /* ignore */ }
        return "UNKNOWN";
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String ip = req.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) ip = req.getHeader("X-Real-IP");
                if (ip == null || ip.isEmpty()) ip = req.getRemoteAddr();
                return ip;
            }
        } catch (Exception e) { /* ignore */ }
        return "unknown";
    }

    private String getRequestParams(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) continue;
            // 跳过 MultipartFile、HttpServletResponse 等不可序列化类型
            if (arg instanceof org.springframework.web.multipart.MultipartFile) {
                sb.append("[FILE]");
            } else if (arg instanceof javax.servlet.http.HttpServletResponse) {
                sb.append("[RESPONSE]");
            } else {
                sb.append(arg).append("; ");
            }
        }
        return sb.toString();
    }

    private String safeTruncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
