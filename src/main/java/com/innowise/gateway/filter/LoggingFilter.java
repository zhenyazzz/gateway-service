package com.innowise.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered{


    @Override
    public int getOrder() {
        return GatewayFilterOrders.LOGGING;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        String requestId = request.getId();
        long start = System.currentTimeMillis();

        log.info("REQUEST: id={} method={} path={} query={}", requestId, method, path, query);

        return chain.filter(exchange)
            .doOnSuccess(unused -> {
                int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
                long tookMs = System.currentTimeMillis() - start;
                log.info("RESPONSE: id={} method={} path={} status={} tookMs={}",
                    requestId, method, path, status, tookMs);
            })
            .doOnError(ex -> {
                long tookMs = System.currentTimeMillis() - start;
                int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
                log.error("ERROR: id={} method={} path={} status={} tookMs={} message={}",
                    requestId, method, path, status, tookMs, ex.getMessage(), ex);
            });
    }

}
