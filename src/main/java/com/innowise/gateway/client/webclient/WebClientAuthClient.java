package com.innowise.gateway.client.webclient;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.innowise.gateway.client.AuthClient;
import com.innowise.gateway.dto.iternal.AuthRegisterRequest;
import com.innowise.gateway.dto.response.RegisterResponse;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WebClientAuthClient implements AuthClient {

    @Qualifier("authServiceClient")
    private final WebClient webClient;

    @Override
    @CircuitBreaker(name = "authService")
    @Retry(name = "authService")
    public Mono<RegisterResponse> register(AuthRegisterRequest request, String idempotencyKey) {
        return webClient.post()
                .uri("/register")
                .header("X-Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("Auth error: " + body))))
                .bodyToMono(RegisterResponse.class);
    }

    @Override
    @Retry(name = "authService")
    public Mono<Void> deleteUser(UUID userId, String idempotencyKey) {
        return webClient.delete()
                .uri("/users/{userId}", userId)
                .header("X-Idempotency-Key", idempotencyKey)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
