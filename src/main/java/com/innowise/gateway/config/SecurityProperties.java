package com.innowise.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "app.security")
@Component
@Getter
@Setter
public class SecurityProperties {

    private List<String> whitelistPaths = List.of();
    @NotBlank
    private String authServicePathPrefix;
}
