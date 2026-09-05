package com.lifelink.common;

/** Mapped to HTTP 403 by {@link GlobalExceptionHandler}. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
