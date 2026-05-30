package com.iliaspiotopoulos.bibliocore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bibliocore.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        int accessTokenTtl
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "biblioCore-api";
        }
        if (accessTokenTtl <= 0) {
            accessTokenTtl = 60;
        }
    }
}