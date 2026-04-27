package com.innowise.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Slf4j
@Component
public class LoggingFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return GatewayFilterOrders.HTTP_ACCESS_LOG;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();
        String query = request.getURI().getQuery();
        String requestId = request.getId();
        long start = System.currentTimeMillis();

        log.info("REQUEST: id={} method={} path={} query={}", requestId, method, path, query);

        return chain.filter(exchange)
            .doOnError(ex -> log.error(
                "CHAIN ERROR: id={} method={} path={} tookMs={} message={}",
                requestId,
                method,
                path,
                System.currentTimeMillis() - start,
                ex.getMessage(),
                ex
            ))
            .doFinally(signal -> {
                long tookMs = System.currentTimeMillis() - start;
                HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                int status = statusCode != null ? statusCode.value() : 0;
                if (signal == SignalType.ON_COMPLETE) {
                    log.info(
                        "RESPONSE: id={} method={} path={} status={} tookMs={}",
                        requestId,
                        method,
                        path,
                        status,
                        tookMs
                    );
                } else if (signal == SignalType.CANCEL) {
                    log.warn(
                        "REQUEST CANCELLED: id={} method={} path={} status={} tookMs={}",
                        requestId,
                        method,
                        path,
                        status,
                        tookMs
                    );
                }
            });
    }
}
