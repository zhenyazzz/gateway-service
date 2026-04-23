package com.innowise.gateway.client;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface OrderClient {
    Mono<Void> cancelUserOrders(UUID userId, String idempotencyKey);
}
