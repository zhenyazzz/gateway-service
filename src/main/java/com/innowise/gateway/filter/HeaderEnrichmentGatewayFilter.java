package com.innowise.gateway.filter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.innowise.gateway.config.SecurityProperties;
import com.innowise.gateway.security.BearerTokenConstants;
import com.innowise.gateway.security.RoleName;
import com.innowise.gateway.security.TokenPayload;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class HeaderEnrichmentGatewayFilter implements GlobalFilter, Ordered {

    private final SecurityProperties securityProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                Authentication authentication = ctx.getAuthentication();
                if (authentication == null
                    || !(authentication.getPrincipal() instanceof TokenPayload(
                        var userId,
                        var email,
                        var rolesFromToken,
                        var rawToken
                    ))) {
                    return chain.filter(exchange);
                }

                if (rawToken == null || rawToken.isBlank()) {
                    return chain.filter(exchange);
                }

                List<RoleName> roles = rolesFromToken == null ? List.of() : rolesFromToken;
                String bearerAuthorization = BearerTokenConstants.BEARER_PREFIX + rawToken;

                ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Email");
                        h.remove("X-User-Roles");
                        h.remove(HttpHeaders.AUTHORIZATION);

                        if (isAuthServicePath(path)) {
                            h.set(HttpHeaders.AUTHORIZATION, bearerAuthorization);
                        } else {
                            if (userId != null) {
                                h.set("X-User-Id", userId.toString());
                            }

                            if (email != null) {
                                h.set("X-User-Email", email);
                            }

                            h.set("X-User-Roles",
                                roles.stream()
                                    .map(RoleName::name)
                                    .collect(Collectors.joining(",")));
                        }
                    })
                    .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            })
            .switchIfEmpty(chain.filter(exchange));
    }

    private boolean isAuthServicePath(String path) {
        return path.startsWith(securityProperties.getAuthServicePathPrefix());
    }

    private boolean isWhitelisted(String path) {
        return securityProperties.getWhitelistPaths()
            .stream()
            .anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.HEADER_ENRICHMENT;
    }
}
