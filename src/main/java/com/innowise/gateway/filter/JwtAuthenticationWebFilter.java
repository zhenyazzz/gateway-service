package com.innowise.gateway.filter;

import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.innowise.gateway.config.SecurityProperties;
import com.innowise.gateway.security.BearerTokenConstants;
import com.innowise.gateway.security.GatewaySecurityExchangeAttributes;
import com.innowise.gateway.exception.TokenRevokedException;
import com.innowise.gateway.security.JwtService;
import com.innowise.gateway.security.RoleName;

import io.jsonwebtoken.JwtException;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationWebFilter implements WebFilter, Ordered {

    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final ServerSecurityContextRepository securityContextRepository;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (isWhitelisted(method, path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BearerTokenConstants.BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        String token = jwtService.extractBearerToken(authorization);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange);
        }

        return jwtService.validateAndExtract(token)
                .flatMap(payload -> {
                    exchange.getAttributes().put(GatewaySecurityExchangeAttributes.TOKEN_PAYLOAD, payload);

                    List<RoleName> roles = payload.roles() == null ? List.of() : payload.roles();
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority(role.name()))
                            .toList();
                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(payload, null, authorities);

                    SecurityContext securityContext = new SecurityContextImpl(authentication);
                    return securityContextRepository.save(exchange, securityContext)
                            .then(Mono.defer(() -> chain.filter(exchange)));
                })
                .onErrorResume(ex -> isJwtAuthenticationFailure(ex), ex -> unauthorized(exchange));
    }

    private static boolean isJwtAuthenticationFailure(Throwable ex) {
        return ex instanceof JwtException
                || ex instanceof TokenRevokedException
                || ex instanceof IllegalArgumentException;
    }

    private boolean isWhitelisted(HttpMethod method, String path) {
        if (method == null) {
            return false;
        }
        Map<HttpMethod, List<String>> whitelist = securityProperties.getWhitelistPaths();
        List<String> methodPaths = whitelist.getOrDefault(method, List.of());
        return methodPaths.stream()
                .anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
