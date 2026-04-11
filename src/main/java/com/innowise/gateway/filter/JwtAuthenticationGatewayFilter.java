package com.innowise.gateway.filter;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.innowise.gateway.config.SecurityProperties;
import com.innowise.gateway.security.BearerTokenConstants;
import com.innowise.gateway.security.JwtService;
import com.innowise.gateway.security.RoleName;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Проверяет JWT и кладёт {@link Authentication} с {@link com.innowise.gateway.security.TokenPayload}
 * (включая сырой JWT в {@link com.innowise.gateway.security.TokenPayload#token()}) в reactive security context.
 * Заголовки downstream не меняет — этим занимается {@link HeaderEnrichmentGatewayFilter}.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
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

                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            })
            .onErrorResume(ex -> unauthorized(exchange));
    }

    private boolean isWhitelisted(String path) {
        return securityProperties.getWhitelistPaths()
            .stream()
            .anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.JWT_AUTHENTICATION;
    }
}
