package com.innowise.gateway.unit;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.innowise.gateway.client.AuthClient;
import com.innowise.gateway.client.UserClient;
import com.innowise.gateway.dto.iternal.AuthRegisterRequest;
import com.innowise.gateway.dto.iternal.UserCreateRequest;
import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.dto.response.RegisterGatewayResponse;
import com.innowise.gateway.dto.response.RegisterResponse;
import com.innowise.gateway.dto.response.UserResponse;
import com.innowise.gateway.exception.CompensationFailedException;
import com.innowise.gateway.mapper.RegisterRequestMapper;
import com.innowise.gateway.service.RegistrationOrchestrator;
import com.innowise.gateway.utils.RegistrationOrchestratorTestDtoFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationOrchestratorTest {

    @Mock
    private AuthClient authClient;
    @Mock
    private UserClient userClient;
    @Mock
    private RegisterRequestMapper registerRequestMapper;

    private RegistrationOrchestrator orchestrator;

    private final RegisterRequest request = RegistrationOrchestratorTestDtoFactory.registerRequest();
    private final String idem = RegistrationOrchestratorTestDtoFactory.idempotencyKey();
    private final AuthRegisterRequest authReq = RegistrationOrchestratorTestDtoFactory.authRegisterRequest();
    private final UUID uid = RegistrationOrchestratorTestDtoFactory.userId();

    @BeforeEach
    void setUp() {
        orchestrator = new RegistrationOrchestrator(authClient, userClient, registerRequestMapper);
    }

    @Test
    void register_whenAuthAndUserSucceed_returnsAggregatedResponse() {
        RegisterResponse authResp = RegistrationOrchestratorTestDtoFactory.registerResponse(uid, request);
        UserCreateRequest createReq = RegistrationOrchestratorTestDtoFactory.userCreateRequest(uid, request);
        UserResponse userResp = RegistrationOrchestratorTestDtoFactory.userResponse(uid, request);
        RegisterGatewayResponse gatewayResp = RegistrationOrchestratorTestDtoFactory.registerGatewayResponse(uid, request);

        when(registerRequestMapper.toAuthRegisterRequest(request)).thenReturn(authReq);
        when(authClient.register(authReq, idem)).thenReturn(Mono.just(authResp));
        when(registerRequestMapper.toUserCreateRequest(uid, request)).thenReturn(createReq);
        when(userClient.createProfile(createReq, idem)).thenReturn(Mono.just(userResp));
        when(registerRequestMapper.toRegisterGatewayResponse(authResp, userResp)).thenReturn(gatewayResp);

        StepVerifier.create(orchestrator.register(request, idem))
                .assertNext(r -> assertThat(r).isEqualTo(gatewayResp))
                .verifyComplete();

        verify(authClient, never()).deleteUser(any(), any());
    }

    @Test
    void register_whenUserProfileFails_deletesAuthUser_andEmitsCompensationFailed() {
        RegisterResponse authResp = RegistrationOrchestratorTestDtoFactory.registerResponse(uid, request);
        UserCreateRequest createReq = RegistrationOrchestratorTestDtoFactory.userCreateRequest(uid, request);
        RuntimeException profileEx = new RuntimeException("user service down");

        when(registerRequestMapper.toAuthRegisterRequest(request)).thenReturn(authReq);
        when(authClient.register(authReq, idem)).thenReturn(Mono.just(authResp));
        when(registerRequestMapper.toUserCreateRequest(uid, request)).thenReturn(createReq);
        when(userClient.createProfile(createReq, idem)).thenReturn(Mono.error(profileEx));
        when(authClient.deleteUser(uid, idem)).thenReturn(Mono.empty());

        StepVerifier.create(orchestrator.register(request, idem))
                .expectErrorMatches(ex -> ex instanceof CompensationFailedException
                        && ex.getMessage().contains("User Service")
                        && ex.getCause() == profileEx)
                .verify();

        verify(authClient).deleteUser(uid, idem);
    }

    @Test
    void register_whenUserProfileFails_andAuthDeleteAlsoFails_stillEmitsCompensationFailed() {
        RegisterResponse authResp = RegistrationOrchestratorTestDtoFactory.registerResponse(uid, request);
        UserCreateRequest createReq = RegistrationOrchestratorTestDtoFactory.userCreateRequest(uid, request);
        RuntimeException profileEx = new RuntimeException("user service down");

        when(registerRequestMapper.toAuthRegisterRequest(request)).thenReturn(authReq);
        when(authClient.register(authReq, idem)).thenReturn(Mono.just(authResp));
        when(registerRequestMapper.toUserCreateRequest(uid, request)).thenReturn(createReq);
        when(userClient.createProfile(createReq, idem)).thenReturn(Mono.error(profileEx));
        when(authClient.deleteUser(uid, idem))
                .thenReturn(Mono.error(new RuntimeException("rollback failed")));

        StepVerifier.create(orchestrator.register(request, idem))
                .expectErrorMatches(ex -> ex instanceof CompensationFailedException
                        && ex.getCause() == profileEx)
                .verify();

        verify(authClient).deleteUser(eq(uid), eq(idem));
    }
}
