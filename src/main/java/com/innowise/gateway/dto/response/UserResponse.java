package com.innowise.gateway.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String surname,
        LocalDate birthDate,
        String email
) {
}
