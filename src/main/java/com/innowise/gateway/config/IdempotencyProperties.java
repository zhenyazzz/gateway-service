package com.innowise.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.idempotency")
@Component
public class IdempotencyProperties {

    private List<IdempotencyRoute> routes = List.of();
}
