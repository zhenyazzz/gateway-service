package com.innowise.gateway.config;

import org.springframework.http.HttpMethod;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IdempotencyRoute {

    private String method;

    private String pathPrefix;

    public boolean matches(HttpMethod httpMethod, String path) {
        if (method == null || pathPrefix == null || path == null) {
            return false;
        }
        if (!path.startsWith(pathPrefix)) {
            return false;
        }
        try {
            return HttpMethod.valueOf(method.trim().toUpperCase()).equals(httpMethod);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
