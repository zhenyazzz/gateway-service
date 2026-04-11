package com.innowise.gateway.security;

import java.util.UUID;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.innowise.gateway.config.JwtProperties;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
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
                .defaultIfEmpty(0L);
    }

    private String userTokenVersionKey(UUID userId) {
        return props.getBlacklistKeyPrefix() + "user-ver:" + userId;
    }
}
