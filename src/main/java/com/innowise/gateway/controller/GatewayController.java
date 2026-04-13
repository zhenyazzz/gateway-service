package com.innowise.gateway.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.dto.response.RegisterGatewayResponse;
import com.innowise.gateway.service.DeletionOrchestrator;
import com.innowise.gateway.service.RegistrationOrchestrator;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
/**
 * Gateway API controller for user registration and account deletion orchestration.
 */
public class GatewayController {

    private final RegistrationOrchestrator registrationOrchestrator;
    private final DeletionOrchestrator deletionOrchestrator;

    /**
     * Registers a new user through orchestrated Auth and User service calls.
     *
     * @param request registration payload
     * @param idempotencyKey idempotency key used to deduplicate retries
     * @return created user aggregate response
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterGatewayResponse>> register(
        @RequestBody RegisterRequest request,
        @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        return registrationOrchestrator.register(request, idempotencyKey).map(ResponseEntity::ok);
    }

    /**
     * Deletes a user account through orchestrated Order, User and Auth service calls.
     *
     * @param userId target account identifier
     * @param idempotencyKey idempotency key used to deduplicate retries
     * @return no-content response when deletion flow completes
     */
    @DeleteMapping("/delete/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID userId, @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
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
