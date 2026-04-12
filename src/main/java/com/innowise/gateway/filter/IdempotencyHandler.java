package com.innowise.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.innowise.gateway.config.IdempotencyProperties;
import com.innowise.gateway.config.IdempotencyRoute;
import com.innowise.gateway.idempotency.CachedResponse;
import com.innowise.gateway.idempotency.IdempotencyResult;
import com.innowise.gateway.service.IdempotencyService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class IdempotencyHandler {

    private final IdempotencyService service;
    private final IdempotencyProperties idempotencyProperties;

    public Mono<Void> handle(ServerWebExchange exchange, Function<ServerWebExchange, Mono<Void>> next) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (!isIdempotent(path, method)) {
            return next.apply(exchange);
        }

        String key = exchange.getRequest().getHeaders().getFirst("X-Idempotency-Key");

        if (key == null || key.isBlank()) {
            return badRequest(exchange);
        }

        String finalKey = buildKey(exchange, key);

        return service.check(finalKey)
                .flatMap(result -> {

                    if (result instanceof IdempotencyResult.Processing) {
                        return conflict(exchange);
                    }

                    if (result instanceof IdempotencyResult.Cached cached) {
                        return writeCached(exchange, cached.response());
                    }

                    return proceedAndCache(exchange, next, finalKey);
                });
    }

    private Mono<Void> proceedAndCache(ServerWebExchange exchange,
                                       Function<ServerWebExchange, Mono<Void>> next,
                                       String key) {

        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bufferFactory = original.bufferFactory();
        StringBuilder bodyAggregate = new StringBuilder();
        AtomicBoolean persisted = new AtomicBoolean(false);

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                Mono<Void> written;
                if (body instanceof Flux<? extends DataBuffer> flux) {
                    written = super.writeWith(
                            flux.map(db -> captureToString(db, bodyAggregate, bufferFactory)));
                } else if (body instanceof Mono<? extends DataBuffer> mono) {
                    written = super.writeWith(
                            mono.map(db -> captureToString(db, bodyAggregate, bufferFactory)));
                } else {
                    written = super.writeWith(body);
                }
                return written.then(persistAfterResponse(key, original, bodyAggregate, persisted));
            }

            @Override
            public Mono<Void> setComplete() {
                return super.setComplete().then(persistAfterResponse(key, original, bodyAggregate, persisted));
            }
        };

        return next.apply(exchange.mutate().response(decorated).build())
                .onErrorResume(e ->
                        service.clear(key).then(Mono.error(e))
                );
    }

    /**
     * Сохраняем после фактической записи тела: {@code next.complete()} часто завершается раньше, чем отработает {@code writeWith}.
     */
    private Mono<Void> persistAfterResponse(
            String key,
            ServerHttpResponse original,
            StringBuilder bodyAggregate,
            AtomicBoolean persisted) {
        return Mono.defer(() -> {
            if (!persisted.compareAndSet(false, true)) {
                return Mono.empty();
            }
            int status = original.getStatusCode() != null ? original.getStatusCode().value() : 200;
            String body = bodyAggregate.toString();
            return service.saveSuccess(key, new CachedResponse(status, body));
        });
    }

    private Mono<Void> writeCached(ServerWebExchange exchange, CachedResponse resp) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(resp.status()));

        String body = resp.body();
        if (body == null || body.isEmpty()) {
            return response.setComplete();
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        return response.writeWith(Mono.just(buffer));
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
        return exchange.getRequest().getMethod() + ":"
                + exchange.getRequest().getPath().value() + ":"
                + key;
    }

    private Mono<Void> badRequest(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> conflict(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
        return exchange.getResponse().setComplete();
    }

    private static DataBuffer captureToString(
            DataBuffer dataBuffer,
            StringBuilder bodyAggregate,
            DataBufferFactory bufferFactory) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        bodyAggregate.append(new String(bytes, StandardCharsets.UTF_8));
        return bufferFactory.wrap(bytes);
    }
}
