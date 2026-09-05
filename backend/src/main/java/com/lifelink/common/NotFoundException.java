package com.lifelink.common;

/** Mapped to HTTP 404 by {@link GlobalExceptionHandler}. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + " " + id + " not found");
    }
}
