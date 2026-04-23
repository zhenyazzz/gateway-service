package com.innowise.gateway.utils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.security.RoleName;


public final class GatewayTestDtoFactory {

    private GatewayTestDtoFactory() {
    }

    public static RegisterRequest validRegisterRequest() {
        return new RegisterRequest(
            "newuser@example.com",
            "Password1a",
            "Ivan",
            "Petrov",
            LocalDate.of(1995, 5, 20),
            "newuser@example.com"
        );
    }

    public static String registerResponseJson(UUID userId, String login) {
        return """
            {
              "user": {
                "id": "%s",
                "login": "%s",
                "roles": ["ROLE_USER"]
              },
              "accessToken": "access-test-token",
              "refreshToken": "refresh-test-token",
              "expiresIn": 900,
              "tokenType": "Bearer"
            }
            """.formatted(userId, login);
    }

    public static String userResponseJson(UUID userId, RegisterRequest request) {
        return """
            {
              "id": "%s",
              "name": "%s",
              "surname": "%s",
              "birthDate": "%s",
              "email": "%s"
            }
            """.formatted(
            userId,
            request.name(),
            request.surname(),
            request.birthDate(),
            request.email()
        );
    }

    public static List<RoleName> defaultRoles() {
        return List.of(RoleName.ROLE_USER);
    }
}
