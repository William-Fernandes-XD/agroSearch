package com.searchagro.exception;

import org.springframework.http.HttpStatus;

public class NominatimException extends RuntimeException {

    private static final long serialVersionUID = 1L;
	private final int upstreamStatus;
    private final HttpStatus responseStatus;

    public NominatimException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, 0, null);
    }

    public NominatimException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, 0, cause);
    }

    public NominatimException(String message, int upstreamStatus) {
        this(message, HttpStatus.BAD_GATEWAY, upstreamStatus, null);
    }

    public NominatimException(String message, HttpStatus responseStatus, Throwable cause) {
        this(message, responseStatus, 0, cause);
    }

    public NominatimException(
            String message,
            HttpStatus responseStatus,
            int upstreamStatus,
            Throwable cause) {
        super(message, cause);
        this.responseStatus = responseStatus;
        this.upstreamStatus = upstreamStatus;
    }

    public HttpStatus getResponseStatus() {
        return responseStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
