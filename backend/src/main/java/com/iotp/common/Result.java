package com.iotp.common;

import java.io.Serializable;

/**
 * 统一响应结果封装类
 * <p>所有API接口返回统一用此类包装，方便前端统一处理</p>
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：200 成功，其他为错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 返回数据 */
    private T data;

    /** 响应时间戳（毫秒） */
    private long timestamp;

    // ==================== 私有构造 ====================

    private Result() {
        this.timestamp = System.currentTimeMillis();
    }

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ==================== 静态工厂方法 ====================

    /** 操作成功（无数据） */
    public static <T> Result<T> ok() {
        return new Result<>(200, "操作成功", null);
    }

    /** 操作成功（带数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /** 操作成功（自定义消息 + 数据） */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 操作失败 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 操作失败（带数据，用于校验错误等场景） */
    public static <T> Result<T> error(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    /** 参数错误 */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /** 未登录/未授权 */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /** 无权限 */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /** 资源不存在 */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }

    /** 服务器内部错误 */
    public static <T> Result<T> serverError(String message) {
        return new Result<>(500, message, null);
    }

    // ==================== Getter / Setter ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
