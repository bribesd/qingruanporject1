package com.enterprise.kb.exception;

/**
 * 业务异常：携带 HTTP 状态码与提示文案，由 GlobalExceptionHandler 统一转成响应。
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
