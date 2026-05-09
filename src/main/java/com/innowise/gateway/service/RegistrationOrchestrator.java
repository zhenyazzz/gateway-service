package com.innowise.gateway.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.innowise.gateway.client.AuthClient;
import com.innowise.gateway.client.UserClient;
import com.innowise.gateway.exception.CompensationFailedException;
import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.dto.response.RegisterGatewayResponse;
import com.innowise.gateway.mapper.RegisterRequestMapper;

import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationOrchestrator {

    private final AuthClient authClient;
    private final UserClient userClient;
    private final RegisterRequestMapper registerRequestMapper;

    public Mono<RegisterGatewayResponse> register(RegisterRequest request, String idempotencyKey) {

        return authClient
                .register(registerRequestMapper.toAuthRegisterRequest(request), idempotencyKey)
                .flatMap(registerResponse -> {
                    UUID userId = registerResponse.user().id();

                    return userClient
                            .createProfile(registerRequestMapper.toUserCreateRequest(userId, request), idempotencyKey)
                            .map(userResponse -> registerRequestMapper.toRegisterGatewayResponse(registerResponse, userResponse))
                            .onErrorResume(profileEx -> {
                                if (profileEx instanceof WebClientResponseException wre) {
                                    log.error(
                                            "Failed to create profile for user {} — user-service status={} body={}",
                                            userId,
                                            wre.getStatusCode(),
                                            wre.getResponseBodyAsString());
                                } else {
                                    log.error("Failed to create profile for user {}!", userId, profileEx);
                                }

                                return authClient
                                        .deleteUserInternal(userId, idempotencyKey)
                                        .onErrorResume(rollbackEx -> {
                                            log.error("CRITICAL ALARM: Rollback completely failed for user {}!", userId, rollbackEx);
                                            return Mono.empty(); 
                                        })
                                        .then(Mono.error(new CompensationFailedException("Registration failed at User Service", profileEx)));
                            });
                });
    }
}
