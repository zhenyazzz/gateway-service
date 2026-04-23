package com.innowise.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.innowise.gateway.config.IdempotencyProperties;
import com.innowise.gateway.config.IdempotencyRoute;
import com.innowise.gateway.idempotency.IdempotencyResult;
import com.innowise.gateway.service.IdempotencyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyHandler {

    private static final byte[] ALREADY_PROCESSED_JSON =
            "{\"message\":\"Already processed\"}".getBytes(StandardCharsets.UTF_8);

    private final IdempotencyService service;
    private final IdempotencyProperties idempotencyProperties;

    public Mono<Void> handle(ServerWebExchange exchange, Function<ServerWebExchange, Mono<Void>> next) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (!isIdempotent(path, method)) {
            log.trace("Idempotency skipped (route not configured): method={} path={}", method, path);
            return next.apply(exchange);
        }

        String key = exchange.getRequest().getHeaders().getFirst("X-Idempotency-Key");

        if (key == null || key.isBlank()) {
            log.warn("Missing X-Idempotency-Key: requestId={} method={} path={}",
                    exchange.getRequest().getId(), method, path);
            return badRequest(exchange);
        }

        String finalKey = buildKey(exchange, key);

        return service.check(finalKey)
                .flatMap(result -> switch (result) {
                    case IdempotencyResult.Processing() -> conflict(exchange);
                    case IdempotencyResult.Done() -> alreadyProcessed(exchange);
                    case IdempotencyResult.NewRequest() -> proceed(exchange, next, finalKey);
                });
    }

    private Mono<Void> proceed(
        ServerWebExchange exchange,
        Function<ServerWebExchange, Mono<Void>> next,
        String key
    ) {
        return next.apply(exchange)
            .then(service.markDone(key))
            .onErrorResume(e ->
                service.clear(key).then(Mono.error(e))
            );
    }

    private boolean isIdempotent(String path, HttpMethod method) {
        if (method == null) {
            return false;
        }
        for (IdempotencyRoute route : idempotencyProperties.getRoutes()) {
            if (route.matches(method, path)) {
                return true;
            }
        }
        return false;
    }

    private String buildKey(ServerWebExchange exchange, String key) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        return method + ":" + path + ":" + key;
    }

    private Mono<Void> badRequest(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> conflict(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> alreadyProcessed(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.CONFLICT);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(ALREADY_PROCESSED_JSON)));
    }

}
