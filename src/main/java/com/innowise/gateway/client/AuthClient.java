package com.innowise.gateway.client;

import java.util.UUID;

import com.innowise.gateway.dto.iternal.AuthRegisterRequest;
import com.innowise.gateway.dto.response.RegisterResponse;

import reactor.core.publisher.Mono;

public interface AuthClient {
    Mono<RegisterResponse> register(AuthRegisterRequest request, String idempotencyKey);

    Mono<Void> deleteUser(UUID userId, String idempotencyKey);

    Mono<Void> deleteUserInternal(UUID userId, String idempotencyKey);
}
