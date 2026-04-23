package com.innowise.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.jwt")
@EnableConfigurationProperties
@Component
public class JwtProperties {

    @NotBlank   
    private String secret;

    @NotBlank
    private String issuer;

    @NotNull
    private Duration accessTokenExpiry;

    @NotNull
    private Duration refreshTokenExpiry;

    @NotBlank
    private String blacklistKeyPrefix;
}
