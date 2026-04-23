package com.innowise.gateway.security;

import java.util.UUID;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.innowise.gateway.config.JwtProperties;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final ReactiveStringRedisTemplate redis;
    private final JwtProperties props;

    public Mono<Long> getUserTokenVersion(UUID userId) {
        if (userId == null) {
            return Mono.just(0L);
        }
        return redis.opsForValue()
                .get(userTokenVersionKey(userId))
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (Exception e) {
                        return 0L;
                    }
                })
                .defaultIfEmpty(0L)
                .onErrorResume(ex -> {
                    log.warn("Token version lookup failed for user {}; assuming 0 (fail-open for availability)", userId, ex);
                    return Mono.just(0L);
                });
    }

    private String userTokenVersionKey(UUID userId) {
        return props.getBlacklistKeyPrefix() + "user-ver:" + userId;
    }
}
