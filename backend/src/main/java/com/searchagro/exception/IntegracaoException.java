package com.searchagro.exception;

import org.springframework.http.HttpStatus;

public class IntegracaoException extends RuntimeException {

    private final HttpStatus httpStatus;

    public IntegracaoException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public IntegracaoException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
