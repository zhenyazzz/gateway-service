package com.innowise.gateway.service;

import com.innowise.gateway.idempotency.CachedResponse;

import reactor.core.publisher.Mono; 

import com.innowise.gateway.idempotency.IdempotencyResult;

public interface IdempotencyService {

    Mono<IdempotencyResult> check(String key);

    Mono<Void> saveSuccess(String key, CachedResponse response);

    Mono<Void> clear(String key);
}