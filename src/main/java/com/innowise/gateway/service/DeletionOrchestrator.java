package com.innowise.gateway.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import com.innowise.gateway.client.AuthClient;
import com.innowise.gateway.client.UserClient;
import com.innowise.gateway.client.OrderClient;
import com.innowise.gateway.exception.CompensationFailedException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeletionOrchestrator {
    private final AuthClient authClient;
    private final UserClient userClient;
    private final OrderClient orderClient;

    public Mono<Void> deleteAccount(UUID userId, String idempotencyKey) {

        return orderClient.cancelUserOrders(userId, idempotencyKey)
            
            .then(Mono.defer(() -> 
                userClient.deleteProfile(userId, idempotencyKey)
            ))
            
            .then(Mono.defer(() -> 
                authClient.deleteUser(userId, idempotencyKey)
                    .onErrorResume(authEx -> {
                        log.error("Failed to delete Auth for user {}. Rolling back User Profile only.", userId, authEx);
                        
                        return userClient.restoreProfile(userId, idempotencyKey)
                            .onErrorResume(rollbackEx -> {
                                log.error("CRITICAL ALARM: Failed to restore profile for user {}!", userId, rollbackEx);
                                return Mono.empty(); 
                            })
                            .then(Mono.error(new CompensationFailedException("Deletion failed at Auth Service", authEx)));
                    })
            ));
    }

}
