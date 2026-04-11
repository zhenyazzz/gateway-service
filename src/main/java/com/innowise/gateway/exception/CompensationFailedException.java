package com.innowise.gateway.exception;

public class CompensationFailedException extends RuntimeException {
    public CompensationFailedException(String message) {
        super(message);
    }

    public CompensationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
