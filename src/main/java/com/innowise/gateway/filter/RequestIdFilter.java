package com.innowise.gateway.filter;

import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    private static final String HEADER = "X-Request-Id";

    @Override
    public int getOrder() {
        return GatewayFilterOrders.REQUEST_ID;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String requestId = exchange.getRequest()
            .getHeaders()
            .getFirst(HEADER);

        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        String finalRequestId = requestId;

        ServerHttpRequest mutatedRequest = exchange.getRequest()
            .mutate()
            .header(HEADER, finalRequestId)
            .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(mutatedRequest)
            .build();

        return chain.filter(mutatedExchange)
            .contextWrite(ctx -> ctx.put(HEADER, finalRequestId));
    }
}
