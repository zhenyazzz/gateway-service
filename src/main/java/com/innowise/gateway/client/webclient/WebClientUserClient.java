package com.innowise.gateway.client.webclient;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
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

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    @Qualifier("userServiceClient")
    private final WebClient webClient;

    @Override
    @CircuitBreaker(name = "userService")
    @Retry(name = "userService")
    public Mono<UserResponse> createProfile(UserCreateRequest request, String idempotencyKey) {
        return webClient.post()
                .uri("/users")
                .headers(h -> addIdempotency(h, idempotencyKey))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(UserResponse.class);
    }

    @Override
    @Retry(name = "userService")
    public Mono<Void> deleteProfile(UUID userId, String idempotencyKey) {
        return webClient.delete()
                .uri("/users/{id}", userId)
                .headers(h -> addIdempotency(h, idempotencyKey))
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    @Override
    @Retry(name = "userService")
    public Mono<UserResponse> restoreProfile(UUID userId, String idempotencyKey) {
        return webClient.post()
                .uri("/users/{id}/restore", userId)
                .headers(h -> addIdempotency(h, idempotencyKey))
                .retrieve()
                .bodyToMono(UserResponse.class);
    }

    private static void addIdempotency(HttpHeaders headers, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set(IDEMPOTENCY_HEADER, idempotencyKey);
        }
    }
}
