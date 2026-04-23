package com.innowise.gateway.dto.response;

import java.util.List;
import java.util.UUID;

import com.innowise.gateway.security.RoleName;

public record RegisterResponse(
        UserInfo user,
        String accessToken,
        String refreshToken,
        int expiresIn,
        String tokenType
) {
    public record UserInfo(
            UUID id,
            String login,
            List<RoleName> roles
    ) {}
}
