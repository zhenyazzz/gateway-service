package com.innowise.gateway.utils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.innowise.gateway.config.JwtProperties;
import com.innowise.gateway.security.BearerTokenConstants;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

public final class JwtTestTokenFactory {

    private JwtTestTokenFactory() {
    }

    public static String bearerAdminToken(JwtProperties props, UUID subjectUserId) {
        return BearerTokenConstants.BEARER_PREFIX + adminAccessToken(props, subjectUserId);
    }

    public static String adminAccessToken(JwtProperties props, UUID subjectUserId) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(props.getSecret()));
        return Jwts.builder()
            .subject(subjectUserId.toString())
            .issuer(props.getIssuer())
            .claim("email", "admin@example.com")
            .claim("roles", List.of("ROLE_ADMIN"))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .issuedAt(Date.from(Instant.now()))
            .signWith(key)
            .compact();
    }
}
