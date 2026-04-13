package com.innowise.gateway.idempotency;


public sealed interface IdempotencyResult permits
        IdempotencyResult.NewRequest,
        IdempotencyResult.Processing,
        IdempotencyResult.Done {

    static IdempotencyResult newRequest() {
        return new NewRequest();
    }

    static IdempotencyResult alreadyProcessing() {
        return new Processing();
    }

    static IdempotencyResult done() {
        return new Done();
    }

    record NewRequest() implements IdempotencyResult {}

    record Processing() implements IdempotencyResult {}

    record Done() implements IdempotencyResult {}
}
