package com.innowise.gateway.client.webclient;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.innowise.gateway.client.OrderClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WebClientOrderClient implements OrderClient {
    @Qualifier("orderServiceClient")
    private final WebClient webClient;

    @Override
    @CircuitBreaker(name = "orderService")
    @Retry(name = "orderService")
    public Mono<Void> cancelUserOrders(UUID userId, String idempotencyKey) {
        return webClient.delete()
                .uri("/orders/user/{userId}", userId)
                .header("X-Idempotency-Key", idempotencyKey)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
