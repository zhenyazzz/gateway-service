package com.innowise.gateway.utils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.innowise.gateway.dto.iternal.AuthRegisterRequest;
import com.innowise.gateway.dto.iternal.UserCreateRequest;
import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.dto.response.RegisterGatewayResponse;
import com.innowise.gateway.dto.response.RegisterResponse;
import com.innowise.gateway.dto.response.UserResponse;
import com.innowise.gateway.security.RoleName;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RegistrationOrchestratorTestDtoFactory {

    private static final UUID DEFAULT_USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    public RegisterRequest registerRequest() {
        return new RegisterRequest(
                "login@mail.com",
                "Password1",
                "Ivan",
                "Petrov",
                LocalDate.of(1990, 1, 1)
        );
    }

    public String idempotencyKey() {
        return "idem-reg";
    }

    public UUID userId() {
        return DEFAULT_USER_ID;
    }

    public AuthRegisterRequest authRegisterRequest() {
        return new AuthRegisterRequest("login@mail.com", "Password1");
    }

    public RegisterResponse.UserInfo userInfo(UUID userId, RegisterRequest request) {
        return new RegisterResponse.UserInfo(userId, request.login(), List.of(RoleName.ROLE_USER));
    }

    public RegisterResponse registerResponse(UUID userId, RegisterRequest request) {
        return new RegisterResponse(
                userInfo(userId, request),
                "access",
                "refresh",
                900,
                "Bearer"
        );
    }

    public UserCreateRequest userCreateRequest(UUID userId, RegisterRequest request) {
        return new UserCreateRequest(
                userId,
                request.name(),
                request.surname(),
                request.birthDate(),
                request.login()
        );
    }

    public UserResponse userResponse(UUID userId, RegisterRequest request) {
        return new UserResponse(
                userId,
                request.name(),
                request.surname(),
                request.birthDate(),
                request.login()
        );
    }

    public RegisterGatewayResponse registerGatewayResponse(UUID userId, RegisterRequest request) {
        return new RegisterGatewayResponse(
                userId,
                request.login(),
                request.name(),
                request.surname(),
                List.of(RoleName.ROLE_USER),
                "access",
                "refresh",
                900
        );
    }
}
