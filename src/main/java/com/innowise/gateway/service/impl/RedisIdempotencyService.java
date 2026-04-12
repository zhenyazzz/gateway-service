package com.innowise.gateway.service.impl;

import java.time.Duration;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import com.innowise.gateway.idempotency.CachedResponse;
import com.innowise.gateway.idempotency.IdempotencyResult;
import com.innowise.gateway.service.IdempotencyService;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService implements IdempotencyService {

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "idempotency:";
    private static final String STATE_PROCESSING = "PROCESSING";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration DONE_TTL = Duration.ofMinutes(30);

    @Override
    public Mono<IdempotencyResult> check(String key) {
        String redisKey = PREFIX + key;

        return redis.opsForValue().get(redisKey)
            .flatMap(value -> {
                if (value.startsWith(STATE_PROCESSING)) {
                    return Mono.just(IdempotencyResult.alreadyProcessing());
                }

                try {
                    CachedResponse resp = objectMapper.readValue(value, CachedResponse.class);
                    return Mono.just(IdempotencyResult.replay(resp));
                } catch (Exception e) {
                    return Mono.error(e);
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

    @Override
    public Mono<Void> saveSuccess(String key, CachedResponse response) {
        String redisKey = PREFIX + key;

        try {
            String value = objectMapper.writeValueAsString(response);
            return redis.opsForValue()
                .set(redisKey, value, DONE_TTL)
                .then();
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> clear(String key) {
        return redis.delete(PREFIX + key).then();
    }
}
