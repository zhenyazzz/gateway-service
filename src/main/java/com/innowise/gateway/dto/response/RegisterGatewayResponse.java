package com.innowise.gateway.dto.response;

import java.util.List;
import java.util.UUID;

import com.innowise.gateway.security.RoleName;

public record RegisterGatewayResponse(
    UUID userId,
    String email,
    String name,
    String surname,
    List<RoleName> roles,
    String accessToken,
    String refreshToken,
    int expiresIn
) {}
