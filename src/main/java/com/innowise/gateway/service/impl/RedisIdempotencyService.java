package com.innowise.gateway.service.impl;

import java.time.Duration;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import com.innowise.gateway.idempotency.IdempotencyResult;
import com.innowise.gateway.service.IdempotencyService;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * Redis-backed implementation of {@link IdempotencyService}.
 *
 * <p>Stores per-request state in Redis with TTL:
 * PROCESSING for in-flight requests and DONE for completed requests.
 *
 * @see IdempotencyService
 */
public class RedisIdempotencyService implements IdempotencyService {

    private final ReactiveStringRedisTemplate redis;

    private static final String PREFIX = "idempotency:";
    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_DONE = "DONE";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration DONE_TTL = Duration.ofMinutes(30);

    /** {@inheritDoc} */
    @Override
    public Mono<IdempotencyResult> check(String key) {
        String redisKey = PREFIX + key;

        return redis.opsForValue().get(redisKey)
            .flatMap(value -> {
                switch (value) {
                    case STATE_PROCESSING:
                        return Mono.just(IdempotencyResult.alreadyProcessing());
                    case STATE_DONE:
                        return Mono.just(IdempotencyResult.done());
                    default:
                        return Mono.error(new IllegalStateException("Unknown state: " + value));
                }
            })
            .switchIfEmpty(
                redis.opsForValue()
                    .setIfAbsent(redisKey, STATE_PROCESSING, PROCESSING_TTL)
                    .flatMap(isNew -> {
                        if (Boolean.TRUE.equals(isNew)) {
                            return Mono.just(IdempotencyResult.newRequest());
                        }
                        return Mono.just(IdempotencyResult.alreadyProcessing());
                    })
            );
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> markDone(String key) {
        String redisKey = PREFIX + key;

        try {
            return redis.opsForValue()
                .set(redisKey, STATE_DONE, DONE_TTL)
                .then()
                .doOnError(e -> log.error("Redis idempotency markDone failed", e));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> clear(String key) {
        return redis.delete(PREFIX + key).then();
    }
}
