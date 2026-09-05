package com.lifelink.common;

/** Mapped to HTTP 429 by {@link GlobalExceptionHandler}. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
