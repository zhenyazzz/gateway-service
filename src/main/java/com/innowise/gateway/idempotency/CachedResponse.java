package com.innowise.gateway.idempotency;

public record CachedResponse(
    int status,
    String body
) {}
