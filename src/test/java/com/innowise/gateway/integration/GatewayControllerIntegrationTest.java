package com.innowise.gateway.integration;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.innowise.gateway.config.JwtProperties;
import com.innowise.gateway.dto.request.RegisterRequest;
import com.innowise.gateway.utils.GatewayTestDtoFactory;
import com.innowise.gateway.utils.JwtTestTokenFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GatewayControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String GATEWAY_REGISTER = "/api/gateway/register";
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String AUTH_REGISTER = "/auth/register";
    private static final String HEADER_X_USER_ID = "X-User-Id";

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @AfterEach
    void tearDown() {
        wireMock.resetAll();
    }

    @Nested
    class Registration {

        @Test
        void success_returnsAggregatedResponse() {
            UUID userId = UUID.randomUUID();
            RegisterRequest body = GatewayTestDtoFactory.validRegisterRequest();

            wireMock.stubFor(post(urlEqualTo(AUTH_REGISTER))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(GatewayTestDtoFactory.registerResponseJson(userId, body.login()))));

            wireMock.stubFor(post(urlEqualTo("/users"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(GatewayTestDtoFactory.userResponseJson(userId, body))));

            webTestClient.post()
                    .uri(GATEWAY_REGISTER)
                    .header(IDEMPOTENCY_HEADER, "idem-reg-ok-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.userId").isEqualTo(userId.toString())
                    .jsonPath("$.email").isEqualTo(body.email())
                    .jsonPath("$.accessToken").isEqualTo("access-test-token");

            wireMock.verify(1, postRequestedFor(urlEqualTo(AUTH_REGISTER)));
            wireMock.verify(1, postRequestedFor(urlEqualTo("/users")));
        }

        @Test
        void whenUserServiceFails_compensatesWithAuthDelete() {
            UUID userId = UUID.randomUUID();
            RegisterRequest body = GatewayTestDtoFactory.validRegisterRequest();

            wireMock.stubFor(post(urlEqualTo(AUTH_REGISTER))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(GatewayTestDtoFactory.registerResponseJson(userId, body.login()))));

            wireMock.stubFor(post(urlEqualTo("/users"))
                    .willReturn(aResponse().withStatus(500).withBody("user down")));

            wireMock.stubFor(delete(urlPathEqualTo("/auth/" + userId))
                    .willReturn(aResponse().withStatus(204)));

            webTestClient.post()
                    .uri(GATEWAY_REGISTER)
                    .header(IDEMPOTENCY_HEADER, "idem-reg-compensate-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus().is5xxServerError();

            wireMock.verify(1, postRequestedFor(urlEqualTo(AUTH_REGISTER)));
            wireMock.verify(1, postRequestedFor(urlEqualTo("/users")));
            wireMock.verify(1, deleteRequestedFor(urlPathEqualTo("/auth/" + userId)));
        }

        @Test
        void withoutIdempotencyKey_returnsBadRequest() {
            webTestClient.post()
                    .uri(GATEWAY_REGISTER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(GatewayTestDtoFactory.validRegisterRequest())
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void sameIdempotencyKey_replaysCachedResponse_withoutSecondDownstreamCall() {
            UUID userId = UUID.randomUUID();
            RegisterRequest body = GatewayTestDtoFactory.validRegisterRequest();
            String idemKey = "idem-cache-" + UUID.randomUUID();

            wireMock.stubFor(post(urlEqualTo(AUTH_REGISTER))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(GatewayTestDtoFactory.registerResponseJson(userId, body.login()))));

            wireMock.stubFor(post(urlEqualTo("/users"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(GatewayTestDtoFactory.userResponseJson(userId, body))));

            String firstResponse = webTestClient.post()
                    .uri(GATEWAY_REGISTER)
                    .header(IDEMPOTENCY_HEADER, idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            String secondResponse = webTestClient.post()
                    .uri(GATEWAY_REGISTER)
                    .header(IDEMPOTENCY_HEADER, idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(secondResponse).isEqualTo(firstResponse);
            wireMock.verify(1, postRequestedFor(urlEqualTo(AUTH_REGISTER)));
            wireMock.verify(1, postRequestedFor(urlEqualTo("/users")));
        }

        @Test
        void whenKeyAlreadyProcessing_returnsConflict() {
            String idemKey = "idem-processing-" + UUID.randomUUID();
            String redisKey = "idempotency:POST:/api/gateway/register:" + idemKey;

            redis.opsForValue().set(redisKey, "PROCESSING").block();

            webTestClient.post()
                    .uri(GATEWAY_REGISTER)
                    .header(IDEMPOTENCY_HEADER, idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(GatewayTestDtoFactory.validRegisterRequest())
                    .exchange()
                    .expectStatus().isEqualTo(409);

            wireMock.verify(0, postRequestedFor(urlEqualTo(AUTH_REGISTER)));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.MethodName.class)
    class Deletion {

        @Test
        void success_returnsNoContent_andCallsUserThenAuthClients() {
            UUID adminSubject = UUID.randomUUID();
            UUID accountUserId = UUID.randomUUID();

            wireMock.stubFor(delete(urlMatching("/orders/user/" + accountUserId))
                    .willReturn(aResponse().withStatus(204)));
            wireMock.stubFor(delete(urlPathEqualTo("/users/" + accountUserId))
                    .willReturn(aResponse().withStatus(204)));
            wireMock.stubFor(delete(urlPathEqualTo("/auth/" + accountUserId))
                    .willReturn(aResponse().withStatus(204)));

            webTestClient.delete()
                    .uri("/api/gateway/delete/{userId}", accountUserId)
                    .header(IDEMPOTENCY_HEADER, "idem-del-ok-" + UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, JwtTestTokenFactory.bearerAdminToken(jwtProperties, adminSubject))
                    .exchange()
                    .expectStatus().isNoContent();

            wireMock.verify(1, deleteRequestedFor(urlMatching("/orders/user/" + accountUserId)));
            wireMock.verify(1, deleteRequestedFor(urlPathEqualTo("/users/" + accountUserId))
                    .withHeader(HEADER_X_USER_ID, equalTo(adminSubject.toString())));
            wireMock.verify(1, deleteRequestedFor(urlPathEqualTo("/auth/" + accountUserId))
                    .withHeader(HttpHeaders.AUTHORIZATION, matching("^Bearer .+")));
        }

        @Test
        void whenOrderServiceFails_returnsServerError_andDoesNotCallUserOrAuth() {
            UUID adminSubject = UUID.randomUUID();
            UUID accountUserId = UUID.randomUUID();

            wireMock.stubFor(delete(urlMatching("/orders/user/" + accountUserId))
                    .willReturn(aResponse().withStatus(500).withBody("order service down")));
            wireMock.stubFor(delete(urlPathEqualTo("/users/" + accountUserId))
                    .willReturn(aResponse().withStatus(204)));

            webTestClient.delete()
                    .uri("/api/gateway/delete/{userId}", accountUserId)
                    .header(IDEMPOTENCY_HEADER, "idem-del-order-fail-" + UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, JwtTestTokenFactory.bearerAdminToken(jwtProperties, adminSubject))
                    .exchange()
                    .expectStatus().is5xxServerError();

            wireMock.verify(1, deleteRequestedFor(urlMatching("/orders/user/" + accountUserId)));
            wireMock.verify(0, deleteRequestedFor(urlPathEqualTo("/users/" + accountUserId))
                    .withHeader(HEADER_X_USER_ID, matching(".+")));
            wireMock.verify(0, deleteRequestedFor(urlPathEqualTo("/auth/" + accountUserId))
                    .withHeader(HttpHeaders.AUTHORIZATION, matching("^Bearer .+")));
        }

        @Test
        void whenUserProfileDeleteFails_returnsServerError_andAuthClientNotCalled() {
            UUID adminSubject = UUID.randomUUID();
            UUID accountUserId = UUID.randomUUID();

            wireMock.stubFor(delete(urlMatching("/orders/user/" + accountUserId))
                    .willReturn(aResponse().withStatus(204)));
            wireMock.stubFor(delete(urlPathEqualTo("/users/" + accountUserId))
                    .withHeader(HEADER_X_USER_ID, equalTo(adminSubject.toString()))
                    .willReturn(aResponse().withStatus(500).withBody("user profile down")));

            webTestClient.delete()
                    .uri("/api/gateway/delete/{userId}", accountUserId)
                    .header(IDEMPOTENCY_HEADER, "idem-del-profile-fail-" + UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, JwtTestTokenFactory.bearerAdminToken(jwtProperties, adminSubject))
                    .exchange()
                    .expectStatus().is5xxServerError();

            wireMock.verify(1, deleteRequestedFor(urlMatching("/orders/user/" + accountUserId)));
            wireMock.verify(1, deleteRequestedFor(urlPathEqualTo("/users/" + accountUserId))
                    .withHeader(HEADER_X_USER_ID, equalTo(adminSubject.toString())));
            wireMock.verify(0, deleteRequestedFor(urlPathEqualTo("/auth/" + accountUserId))
                    .withHeader(HttpHeaders.AUTHORIZATION, matching("^Bearer .+")));
        }

        @Test
        void whenAuthDeleteFails_compensatesWithProfileRestore() {
            UUID adminSubject = UUID.randomUUID();
            UUID accountUserId = UUID.randomUUID();

            wireMock.stubFor(delete(urlMatching("/orders/user/" + accountUserId))
                    .willReturn(aResponse().withStatus(204)));
            wireMock.stubFor(delete(urlPathEqualTo("/users/" + accountUserId))
                    .withHeader(HEADER_X_USER_ID, equalTo(adminSubject.toString()))
                    .willReturn(aResponse().withStatus(204)));
            wireMock.stubFor(delete(urlPathEqualTo("/auth/" + accountUserId))
                    .withHeader(HttpHeaders.AUTHORIZATION, matching("^Bearer .+"))
                    .willReturn(aResponse().withStatus(500).withBody("auth down")));
            RegisterRequest restoreBody = GatewayTestDtoFactory.validRegisterRequest();
            wireMock.stubFor(post(urlPathEqualTo("/users/" + accountUserId + "/restore"))
                    .withHeader(HEADER_X_USER_ID, equalTo(adminSubject.toString()))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(GatewayTestDtoFactory.userResponseJson(accountUserId, restoreBody))));

            webTestClient.delete()
                    .uri("/api/gateway/delete/{userId}", accountUserId)
                    .header(IDEMPOTENCY_HEADER, "idem-del-auth-fail-" + UUID.randomUUID())
                    .header(HttpHeaders.AUTHORIZATION, JwtTestTokenFactory.bearerAdminToken(jwtProperties, adminSubject))
                    .exchange()
                    .expectStatus().is5xxServerError();

            wireMock.verify(1, deleteRequestedFor(urlMatching("/orders/user/" + accountUserId)));
            wireMock.verify(1, deleteRequestedFor(urlPathEqualTo("/users/" + accountUserId))
                    .withHeader(HEADER_X_USER_ID, equalTo(adminSubject.toString())));
            wireMock.verify(1, deleteRequestedFor(urlPathEqualTo("/auth/" + accountUserId))
                    .withHeader(HttpHeaders.AUTHORIZATION, matching("^Bearer .+")));
            wireMock.verify(1, postRequestedFor(urlPathEqualTo("/users/" + accountUserId + "/restore"))
                    .withHeader(HEADER_X_USER_ID, equalTo(adminSubject.toString())));
        }

        @Test
        void withoutIdempotencyKey_returnsBadRequest() {
            UUID adminSubject = UUID.randomUUID();
            UUID accountUserId = UUID.randomUUID();

            webTestClient.delete()
                    .uri("/api/gateway/delete/{userId}", accountUserId)
                    .header(HttpHeaders.AUTHORIZATION, JwtTestTokenFactory.bearerAdminToken(jwtProperties, adminSubject))
                    .exchange()
                    .expectStatus().isBadRequest();

            wireMock.verify(0, deleteRequestedFor(urlMatching("/orders/.*")));
            wireMock.verify(0, deleteRequestedFor(urlPathEqualTo("/users/" + accountUserId)));
        }
    }
}
