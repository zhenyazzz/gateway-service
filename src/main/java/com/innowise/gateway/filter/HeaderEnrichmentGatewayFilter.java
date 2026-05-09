package com.innowise.gateway.filter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.innowise.gateway.config.SecurityProperties;
import com.innowise.gateway.security.BearerTokenConstants;
import com.innowise.gateway.security.GatewaySecurityExchangeAttributes;
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
        HttpMethod method = request.getMethod();

        if (isWhitelisted(method, path)) {
            return chain.filter(exchange);
        }

        Object tokenAttr = exchange.getAttribute(GatewaySecurityExchangeAttributes.TOKEN_PAYLOAD);
        if (tokenAttr instanceof TokenPayload payloadFromExchange
                && payloadFromExchange.token() != null
                && !payloadFromExchange.token().isBlank()) {
            return chain.filter(exchange.mutate()
                    .request(buildDownstreamRequest(request, path, payloadFromExchange))
                    .build());
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

                TokenPayload fromContext = new TokenPayload(userId, email, rolesFromToken, rawToken);
                return chain.filter(exchange.mutate()
                        .request(buildDownstreamRequest(request, path, fromContext))
                        .build());
            })
            .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
    }

    private ServerHttpRequest buildDownstreamRequest(ServerHttpRequest request, String path, TokenPayload payload) {
        List<RoleName> roles = payload.roles() == null ? List.of() : payload.roles();
        String bearerAuthorization = BearerTokenConstants.BEARER_PREFIX + payload.token();

        return request.mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                    h.remove("X-User-Roles");
                    h.remove(HttpHeaders.AUTHORIZATION);

                    if (isAuthServicePath(path)) {
                        h.set(HttpHeaders.AUTHORIZATION, bearerAuthorization);
                    } else {
                        if (payload.userId() != null) {
                            h.set("X-User-Id", payload.userId().toString());
                        }

                        if (payload.email() != null) {
                            h.set("X-User-Email", payload.email());
                        }

                        h.set("X-User-Roles",
                                roles.stream()
                                        .map(RoleName::name)
                                        .collect(Collectors.joining(",")));
                    }
                })
                .build();
    }

    private boolean isAuthServicePath(String path) {
        return path.startsWith(securityProperties.getAuthServicePathPrefix());
    }

    private boolean isWhitelisted(HttpMethod method, String path) {
        if (method == null) {
            return false;
        }
        Map<HttpMethod, List<String>> whitelist = securityProperties.getWhitelistPaths();
        List<String> methodPaths = whitelist.getOrDefault(method, List.of());
        return methodPaths.stream()
            .anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.HEADER_ENRICHMENT;
    }
}
