package com.lifelink.common;

/** Mapped to HTTP 400 by {@link GlobalExceptionHandler}. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
