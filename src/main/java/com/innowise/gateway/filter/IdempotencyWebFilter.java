package com.innowise.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class IdempotencyWebFilter implements WebFilter, Ordered {

    private final IdempotencyHandler idempotencyHandler;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return idempotencyHandler.handle(exchange, chain::filter);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.IDEMPOTENCY;
    }
}
