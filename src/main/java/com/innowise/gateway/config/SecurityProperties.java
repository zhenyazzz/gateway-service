package com.innowise.gateway.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@ConfigurationProperties(prefix = "app.security")
@Component
@Data
public class SecurityProperties {

    private Map<HttpMethod, List<String>> whitelistPaths = Map.of();

    @NotBlank
    private String authServicePathPrefix;
}
