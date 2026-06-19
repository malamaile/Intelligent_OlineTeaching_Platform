package com.iotp.common;

/**
 * 业务异常，统一由 GlobalExceptionHandler 拦截处理
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 422; // 默认：业务逻辑校验失败
    }

    public int getCode() {
        return code;
    }
}
