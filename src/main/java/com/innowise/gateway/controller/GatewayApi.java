package com.innowise.gateway.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.dto.response.RegisterGatewayResponse;

import reactor.core.publisher.Mono;

/**
 * HTTP API exposed by the gateway for orchestrated user lifecycle operations
 * under {@code /api/users}.
 */
@RequestMapping("/api/users")
public interface GatewayApi {

    /**
     * Registers a new user through orchestrated Auth and User service calls.
     *
     * @param request registration payload
     * @param idempotencyKey idempotency key used to deduplicate retries
     * @return created user aggregate response
     */
    @PostMapping("")
    Mono<ResponseEntity<RegisterGatewayResponse>> register(
            @RequestBody RegisterRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey);

    /**
     * Deletes a user account through orchestrated Order, User and Auth service calls.
     *
     * @param userId target account identifier
     * @param idempotencyKey idempotency key used to deduplicate retries
     * @return no-content response when deletion flow completes
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey);
}
