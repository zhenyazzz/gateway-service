package com.innowise.gateway.filter;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.innowise.gateway.config.SecurityProperties;
import com.innowise.gateway.security.BearerTokenConstants;
import com.innowise.gateway.exception.TokenRevokedException;
import com.innowise.gateway.security.JwtService;
import com.innowise.gateway.security.RoleName;

import io.jsonwebtoken.JwtException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter, Ordered {

    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BearerTokenConstants.BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        String token = jwtService.extractBearerToken(authorization);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange);
        }

        return jwtService.validateAndExtract(token)
                .flatMap(payload -> {
                    List<RoleName> roles = payload.roles() == null ? List.of() : payload.roles();
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority(role.name()))
                            .toList();
                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(payload, null, authorities);

                    return Mono.defer(() -> chain.filter(exchange))
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
                .onErrorResume(ex -> isJwtAuthenticationFailure(ex), ex -> {
                    log.debug("JWT validation failed: {}", ex.getMessage());
                    return unauthorized(exchange);
                });
    }

    private static boolean isJwtAuthenticationFailure(Throwable ex) {
        return ex instanceof JwtException
                || ex instanceof TokenRevokedException
                || ex instanceof IllegalArgumentException;
    }

    private boolean isWhitelisted(String path) {
        return securityProperties.getWhitelistPaths()
                .stream()
                .anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
