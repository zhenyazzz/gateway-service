package com.innowise.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.innowise.gateway.security.BearerTokenConstants;
import com.innowise.gateway.security.RoleName;
import com.innowise.gateway.security.TokenPayload;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.stream.Collectors;

@Slf4j
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

    private ReactorClientHttpConnector clientHttpConnector() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(5))
                        .addHandlerLast(new WriteTimeoutHandler(5)));
        return new ReactorClientHttpConnector(httpClient);
    }

    @Bean("authServiceClient")
    public WebClient authClient() {
        return WebClient.builder()
                .clientConnector(clientHttpConnector())
                .baseUrl(authUrl)
                .filter(requestIdFilter())
                .filter(authFilter())
                .build();
    }

    @Bean("userServiceClient")
    public WebClient userClient() {
        return WebClient.builder()
                .clientConnector(clientHttpConnector())
                .baseUrl(userUrl)
                .filter(requestIdFilter())
                .filter(internalFilter())
                .build();
    }

    @Bean("orderServiceClient")
    public WebClient orderClient() {
        return WebClient.builder()
                .clientConnector(clientHttpConnector())
                .baseUrl(orderUrl)
                .filter(requestIdFilter())
                .filter(internalFilter())
                .build();
    }

    private Mono<TokenPayload> currentPayload() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(UsernamePasswordAuthenticationToken.class::isInstance)
                .cast(UsernamePasswordAuthenticationToken.class)
                .map(Authentication::getPrincipal)
                .filter(TokenPayload.class::isInstance)
                .cast(TokenPayload.class);
    }

    private ExchangeFilterFunction requestIdFilter() {
        return (request, next) -> Mono.deferContextual(ctx -> {
            ClientRequest.Builder builder = ClientRequest.from(request);
            String requestId = ctx.getOrDefault(REQUEST_ID_HEADER, null);
            
            if (requestId != null) {
                builder.header(REQUEST_ID_HEADER, requestId);
            }
            return next.exchange(builder.build());
        });
    }

    private ExchangeFilterFunction authFilter() {
        return (request, next) -> {
            ClientRequest.Builder builder = ClientRequest.from(request);
            builder.headers(h -> h.remove(HttpHeaders.AUTHORIZATION));

            return currentPayload()
                    .doOnNext(payload -> {
                        if (payload.token() != null) {
                            builder.header(HttpHeaders.AUTHORIZATION, BearerTokenConstants.BEARER_PREFIX + payload.token());
                        }
                    })
                    .then(Mono.defer(() -> next.exchange(builder.build())));
        };
    }

    private ExchangeFilterFunction internalFilter() {
        return (request, next) -> {
            ClientRequest.Builder builder = ClientRequest.from(request);
            builder.headers(h -> {
                h.remove(HEADER_USER_ID);
                h.remove(HEADER_USER_EMAIL);
                h.remove(HEADER_USER_ROLES);
            });

            return currentPayload()
                    .doOnNext(payload -> {
                        if (payload.userId() != null) {
                            builder.header(HEADER_USER_ID, payload.userId().toString());
                        }
                        if (payload.email() != null) {
                            builder.header(HEADER_USER_EMAIL, payload.email());
                        }
                        if (payload.roles() != null && !payload.roles().isEmpty()) {
                            builder.header(HEADER_USER_ROLES, payload.roles().stream()
                                    .map(RoleName::name)
                                    .collect(Collectors.joining(",")));
                        }
                    })
                    .then(Mono.defer(() -> next.exchange(builder.build())));
        };
    }
}