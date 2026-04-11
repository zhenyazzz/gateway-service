package com.innowise.gateway.security;

import org.springframework.stereotype.Service;

import com.innowise.gateway.config.JwtProperties;
import com.innowise.gateway.exception.TokenRevokedException;

import lombok.RequiredArgsConstructor;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;
    private final TokenBlacklistService blacklist;
    private SecretKey signingKey;

    @PostConstruct
    private void initSigningKey() {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(props.getSecret()));
    }

    public String extractBearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        return authorization.startsWith(BearerTokenConstants.BEARER_PREFIX)
                ? authorization.substring(BearerTokenConstants.BEARER_PREFIX.length())
                : authorization;
    }

    public Mono<TokenPayload> validateAndExtract(String token) {
        return Mono.fromCallable(() -> parseClaims(token))
                .flatMap(claims -> validateVersionAndMapPayload(claims, token));
    }

    private Mono<TokenPayload> validateVersionAndMapPayload(Claims claims, String rawToken) {
        UUID userId = UUID.fromString(claims.getSubject());
        long tokenVersion = Optional.ofNullable(claims.get("tokenVersion", Number.class))
                .map(Number::longValue)
                .orElse(0L);

        return blacklist.getUserTokenVersion(userId)
                .flatMap(currentVersion -> tokenVersion == currentVersion
                        ? Mono.just(mapToPayload(claims, userId, rawToken))
                        : Mono.error(new TokenRevokedException("Token revoked")));
    }

    private static TokenPayload mapToPayload(Claims claims, UUID userId, String rawToken) {
        @SuppressWarnings("unchecked")
        List<String> roles = Optional.ofNullable(claims.get("roles", List.class))
                .orElse(List.of());

        return new TokenPayload(
                userId,
                claims.get("email", String.class),
                roles.stream().map(RoleName::valueOf).toList(),
                rawToken);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(props.getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
