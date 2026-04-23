package com.innowise.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RequestAlreadyProcessingException extends RuntimeException {

    public RequestAlreadyProcessingException(String message) {
        super(message);
    }
}
