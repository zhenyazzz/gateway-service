package com.innowise.gateway.service;

import reactor.core.publisher.Mono; 

import com.innowise.gateway.idempotency.IdempotencyResult;

public interface IdempotencyService {

    Mono<IdempotencyResult> check(String key);

    Mono<Void> markDone(String key);

    Mono<Void> clear(String key);
}