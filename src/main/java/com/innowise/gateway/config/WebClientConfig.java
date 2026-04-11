package com.innowise.gateway.config;

import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.innowise.gateway.security.BearerTokenConstants;
import com.innowise.gateway.security.TokenPayload;

import reactor.core.publisher.Mono;

import com.innowise.gateway.security.RoleName;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class WebClientConfig {

    @Value("${app.services.auth-url}")
    private String authUrl;

    @Value("${app.services.user-url}")
    private String userUrl;

    @Value("${app.services.order-url}")
    private String orderUrl;

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Bean("authServiceClient")
    public WebClient authClient() {
        return WebClient.builder()
            .baseUrl(authUrl)
            .filter(requestIdFilter())
            .filter(authFilter())
            .build();
    }

    @Bean("userServiceClient")
    public WebClient userClient() {
        return WebClient.builder()
            .baseUrl(userUrl)
            .filter(requestIdFilter())
            .filter(internalFilter())
            .build();
    }

    @Bean("orderServiceClient")
    public WebClient orderClient() {
        return WebClient.builder()
            .baseUrl(orderUrl)
            .filter(requestIdFilter())
            .filter(internalFilter())
            .build();
    }

    private Mono<TokenPayload> currentPayload() {
        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication())
            .cast(UsernamePasswordAuthenticationToken.class)
            .map(auth -> (TokenPayload) auth.getPrincipal());
    }

    private ExchangeFilterFunction requestIdFilter() {
        return (request, next) ->
            Mono.deferContextual(ctx -> {

                ClientRequest.Builder builder = ClientRequest.from(request);

                String requestId = ctx.getOrDefault(REQUEST_ID_HEADER, null);

                if (requestId != null) {
                    builder.header(REQUEST_ID_HEADER, requestId);
                }

                return next.exchange(builder.build());
            });
    }

    private ExchangeFilterFunction authFilter() {
        return (request, next) ->
            currentPayload()
                .defaultIfEmpty(null)
                .flatMap(payload -> {

                    ClientRequest.Builder builder = ClientRequest.from(request);

                    builder.headers(h -> h.remove(HttpHeaders.AUTHORIZATION));

                    if (payload != null && payload.token() != null) {
                        builder.header(
                            HttpHeaders.AUTHORIZATION,
                            BearerTokenConstants.BEARER_PREFIX + payload.token()
                        );
                    }

                    return next.exchange(builder.build());
                });
    }

    private ExchangeFilterFunction internalFilter() {
        return (request, next) ->
            currentPayload()
                .defaultIfEmpty(null)
                .flatMap(payload -> {

                    ClientRequest.Builder builder = ClientRequest.from(request);

                    builder.headers(h -> {
                        h.remove(HEADER_USER_ID);
                        h.remove(HEADER_USER_EMAIL);
                        h.remove(HEADER_USER_ROLES);
                    });

                    if (payload != null) {

                        if (payload.userId() != null) {
                            builder.header(HEADER_USER_ID, payload.userId().toString());
                        }

                        if (payload.email() != null) {
                            builder.header(HEADER_USER_EMAIL, payload.email());
                        }

                        if (payload.roles() != null && !payload.roles().isEmpty()) {
                            builder.header(
                                HEADER_USER_ROLES,
                                payload.roles().stream()
                                    .map(RoleName::name)
                                    .collect(Collectors.joining(","))
                            );
                        }
                    }

                    return next.exchange(builder.build());
                });
    }
}

