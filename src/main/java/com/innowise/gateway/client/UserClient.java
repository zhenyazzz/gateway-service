package com.innowise.gateway.client;

import java.util.UUID;

import com.innowise.gateway.dto.iternal.UserCreateRequest;
import com.innowise.gateway.dto.response.UserResponse;

import reactor.core.publisher.Mono;

public interface UserClient {
    Mono<UserResponse> createProfile(UserCreateRequest request, String idempotencyKey);

    Mono<Void> deleteProfile(UUID userId, String idempotencyKey);

    Mono<Void> restoreProfile(UUID userId, String idempotencyKey);
}
