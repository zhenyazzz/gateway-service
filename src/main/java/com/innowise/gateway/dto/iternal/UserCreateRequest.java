package com.innowise.gateway.dto.iternal;

import java.time.LocalDate;
import java.util.UUID;

public record UserCreateRequest(
    UUID userId,
    String name,
    String surname,
    LocalDate birthDate,
    String email
) {

}
