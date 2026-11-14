package com.searchagro.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final int upstreamStatus;

    public ApiException(String message) {
        this(message, HttpStatus.BAD_REQUEST, 0, null);
    }

    public ApiException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_REQUEST, 0, cause);
    }

    public ApiException(String message, HttpStatus status) {
        this(message, status, 0, null);
    }

    public ApiException(String message, int upstreamStatus) {
        this(message, HttpStatus.BAD_GATEWAY, upstreamStatus, null);
    }

    public ApiException(String message, HttpStatus status, Throwable cause) {
        this(message, status, 0, cause);
    }

    public ApiException(String message, HttpStatus status, int upstreamStatus, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.upstreamStatus = upstreamStatus;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
