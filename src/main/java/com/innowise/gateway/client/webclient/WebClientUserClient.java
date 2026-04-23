package com.innowise.gateway.client.webclient;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.innowise.gateway.client.UserClient;
import com.innowise.gateway.dto.iternal.UserCreateRequest;
import com.innowise.gateway.dto.response.UserResponse;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WebClientUserClient implements UserClient {

    @Qualifier("userServiceClient")
    private final WebClient webClient;

    @Override
    @CircuitBreaker(name = "userService")
    @Retry(name = "userService")
    public Mono<UserResponse> createProfile(UserCreateRequest request, String idempotencyKey) {
        return webClient.post()
                .uri("/users")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserResponse.class);
    }

    @Override
    @Retry(name = "userService")
    public Mono<Void> deleteProfile(UUID userId, String idempotencyKey) {
        return webClient.delete()
                .uri("/users/{id}", userId)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    @Override
    @Retry(name = "userService")
    public Mono<UserResponse> restoreProfile(UUID userId, String idempotencyKey) {
        return webClient.post()
                .uri("/users/{id}/restore", userId)
                .retrieve()
                .bodyToMono(UserResponse.class);
    }
}
