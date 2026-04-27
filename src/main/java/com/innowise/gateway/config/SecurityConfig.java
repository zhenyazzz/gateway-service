package com.innowise.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String EXCHANGE_SECURITY_CONTEXT = SecurityConfig.class.getName() + ".securityContext";

    private final SecurityProperties securityProperties;

    @Bean
    ServerSecurityContextRepository serverSecurityContextRepository() {
        return new ServerSecurityContextRepository() {
            @Override
            public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
                if (context.getAuthentication() != null) {
                    exchange.getAttributes().put(EXCHANGE_SECURITY_CONTEXT, context);
                } else {
                    exchange.getAttributes().remove(EXCHANGE_SECURITY_CONTEXT);
                }
                return Mono.empty();
            }

            @Override
            public Mono<SecurityContext> load(ServerWebExchange exchange) {
                SecurityContext c = (SecurityContext) exchange.getAttributes().get(EXCHANGE_SECURITY_CONTEXT);
                return c != null ? Mono.just(c) : Mono.empty();
            }
        };
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ServerSecurityContextRepository serverSecurityContextRepository) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(serverSecurityContextRepository)
                .authorizeExchange(exchange -> {
                    securityProperties.getWhitelistPaths().forEach((method, paths) -> {
                        if (paths == null || paths.isEmpty()) {
                            return;
                        }
                        String[] pathArray = paths.toArray(String[]::new);
                        exchange.pathMatchers(method, pathArray).permitAll();
                    });
                    exchange.anyExchange().authenticated();
                })
                .build();
    }
}
