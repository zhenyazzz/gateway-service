package com.innowise.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.innowise.gateway.idempotency.CachedResponse;
import com.innowise.gateway.idempotency.IdempotencyResult;
import com.innowise.gateway.service.IdempotencyService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter implements GlobalFilter, Ordered {

    private final IdempotencyService service;

    @Override
    public int getOrder() {
        return GatewayFilterOrders.IDEMPOTENCY;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (!isIdempotent(path, method)) {
            return chain.filter(exchange);
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

                return proceedAndCache(exchange, chain, finalKey);
            });
    }

    private Mono<Void> proceedAndCache(ServerWebExchange exchange,
                                       GatewayFilterChain chain,
                                       String key) {

        ServerHttpResponse original = exchange.getResponse();

        DataBufferFactory bufferFactory = original.bufferFactory();

        AtomicReference<String> bodyRef = new AtomicReference<>("");

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

                if (body instanceof Flux<? extends DataBuffer> flux) {

                    return super.writeWith(
                        flux.map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);

                            String bodyStr = new String(bytes, StandardCharsets.UTF_8);
                            bodyRef.set(bodyStr);

                            return bufferFactory.wrap(bytes);
                        })
                    );
                }

                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build())
            .then(
                service.saveSuccess(key,
                    new CachedResponse(
                        original.getStatusCode() != null ? original.getStatusCode().value() : 200,
                        bodyRef.get()
                    )
                )
            )
            .onErrorResume(e ->
                service.clear(key).then(Mono.error(e))
            );
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
        return (HttpMethod.POST.equals(method) && path.startsWith("/api/gateway/register"))
            || (HttpMethod.DELETE.equals(method) && path.startsWith("/api/gateway/delete"));
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
}
