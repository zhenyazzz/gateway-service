package com.innowise.gateway.security;

import java.util.List;
import java.util.UUID;

public record TokenPayload(
        UUID userId,
        String email,
        List<RoleName> roles,
        String token
) {}

