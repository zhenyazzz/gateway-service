package com.innowise.gateway.service;

import reactor.core.publisher.Mono; 

import com.innowise.gateway.idempotency.IdempotencyResult;

/**
 * Contract for idempotency state management for gateway operations.
 */
public interface IdempotencyService {

    /**
     * Checks current idempotency state for the given operation key.
     *
     * @param key unique idempotency key for an operation
     * @return state describing whether request is new, processing, or already done
     */
    Mono<IdempotencyResult> check(String key);

    /**
     * Marks operation as successfully completed.
     *
     * @param key unique idempotency key for an operation
     * @return completion signal
     */
    Mono<Void> markDone(String key);

    /**
     * Clears idempotency state for the given key.
     *
     * @param key unique idempotency key for an operation
     * @return completion signal
     */
    Mono<Void> clear(String key);
}