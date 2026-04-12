package com.innowise.gateway.idempotency;


public sealed interface IdempotencyResult permits
        IdempotencyResult.NewRequest,
        IdempotencyResult.Processing,
        IdempotencyResult.Cached {

    static IdempotencyResult newRequest() {
        return new NewRequest();
    }

    static IdempotencyResult alreadyProcessing() {
        return new Processing();
    }

    static IdempotencyResult replay(CachedResponse response) {
        return new Cached(response);
    }

    record NewRequest() implements IdempotencyResult {}

    record Processing() implements IdempotencyResult {}

    record Cached(CachedResponse response) implements IdempotencyResult {}
}
