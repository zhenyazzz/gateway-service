package com.innowise.gateway.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.dto.response.RegisterGatewayResponse;
import com.innowise.gateway.service.DeletionOrchestrator;
import com.innowise.gateway.service.RegistrationOrchestrator;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class GatewayController implements GatewayApi {

    private final RegistrationOrchestrator registrationOrchestrator;
    private final DeletionOrchestrator deletionOrchestrator;

    @Override
    public Mono<ResponseEntity<RegisterGatewayResponse>> register(
            RegisterRequest request,
            String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        return registrationOrchestrator.register(request, idempotencyKey).map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Void>> delete(UUID userId, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        return deletionOrchestrator.deleteAccount(userId, idempotencyKey)
            .thenReturn(ResponseEntity.noContent().build());
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Idempotency-Key is required");
        }
    }
}
