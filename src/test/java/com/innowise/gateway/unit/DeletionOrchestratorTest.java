package com.innowise.gateway.unit;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.innowise.gateway.client.AuthClient;
import com.innowise.gateway.client.OrderClient;
import com.innowise.gateway.client.UserClient;
import com.innowise.gateway.exception.CompensationFailedException;
import com.innowise.gateway.service.DeletionOrchestrator;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletionOrchestratorTest {

    @Mock
    private OrderClient orderClient;
    @Mock
    private UserClient userClient;
    @Mock
    private AuthClient authClient;

    private DeletionOrchestrator orchestrator;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final String idem = "idem-key";

    @BeforeEach
    void setUp() {
        orchestrator = new DeletionOrchestrator(authClient, userClient, orderClient);
    }

    @Test
    void deleteAccount_whenAllStepsSucceed_completesEmpty() {
        when(orderClient.cancelUserOrders(userId, idem)).thenReturn(Mono.empty());
        when(userClient.deleteProfile(userId, idem)).thenReturn(Mono.empty());
        when(authClient.deleteUser(userId, idem)).thenReturn(Mono.empty());

        StepVerifier.create(orchestrator.deleteAccount(userId, idem))
                .verifyComplete();

        InOrder inOrder = inOrder(orderClient, userClient, authClient);
        inOrder.verify(orderClient).cancelUserOrders(userId, idem);
        inOrder.verify(userClient).deleteProfile(userId, idem);
        inOrder.verify(authClient).deleteUser(userId, idem);
        verify(userClient, never()).restoreProfile(any(), any());
    }

    @Test
    void deleteAccount_whenOrderServiceFails_doesNotCallUserOrAuth() {
        when(orderClient.cancelUserOrders(userId, idem))
                .thenReturn(Mono.error(new RuntimeException("order down")));

        StepVerifier.create(orchestrator.deleteAccount(userId, idem))
                .expectError(RuntimeException.class)
                .verify();

        verify(userClient, never()).deleteProfile(any(), any());
        verify(authClient, never()).deleteUser(any(), any());
        verify(userClient, never()).restoreProfile(any(), any());
    }

    @Test
    void deleteAccount_whenUserProfileDeleteFails_emitsCompensationFailed_andDoesNotCallAuth() {
        when(orderClient.cancelUserOrders(userId, idem)).thenReturn(Mono.empty());
        when(userClient.deleteProfile(userId, idem))
                .thenReturn(Mono.error(new RuntimeException("user down")));

        StepVerifier.create(orchestrator.deleteAccount(userId, idem))
                .expectErrorMatches(ex -> ex instanceof CompensationFailedException
                        && ex.getMessage().contains("User Profile")
                        && ex.getCause() instanceof RuntimeException)
                .verify();

        verify(authClient, never()).deleteUser(any(), any());
        verify(userClient, never()).restoreProfile(any(), any());
    }

    @Test
    void deleteAccount_whenAuthDeleteFails_restoresProfile_andEmitsCompensationFailed() {
        when(orderClient.cancelUserOrders(userId, idem)).thenReturn(Mono.empty());
        when(userClient.deleteProfile(userId, idem)).thenReturn(Mono.empty());
        when(authClient.deleteUser(userId, idem))
                .thenReturn(Mono.error(new RuntimeException("auth down")));
        when(userClient.restoreProfile(userId, idem)).thenReturn(Mono.empty());

        StepVerifier.create(orchestrator.deleteAccount(userId, idem))
                .expectErrorMatches(ex -> ex instanceof CompensationFailedException
                        && ex.getMessage().contains("Auth Service")
                        && ex.getCause() instanceof RuntimeException)
                .verify();

        verify(userClient).restoreProfile(userId, idem);
    }

    @Test
    void deleteAccount_whenAuthFails_andRestoreFails_stillEmitsCompensationFailed() {
        when(orderClient.cancelUserOrders(userId, idem)).thenReturn(Mono.empty());
        when(userClient.deleteProfile(userId, idem)).thenReturn(Mono.empty());
        when(authClient.deleteUser(userId, idem))
                .thenReturn(Mono.error(new RuntimeException("auth down")));
        when(userClient.restoreProfile(userId, idem))
                .thenReturn(Mono.error(new RuntimeException("restore failed")));

        StepVerifier.create(orchestrator.deleteAccount(userId, idem))
                .expectError(CompensationFailedException.class)
                .verify();

        verify(userClient).restoreProfile(eq(userId), eq(idem));
    }
}
