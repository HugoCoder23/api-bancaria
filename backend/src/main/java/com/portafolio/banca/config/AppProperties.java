package com.portafolio.banca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Idempotency idempotency, Seed seed) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Idempotency(long ttlHours) {}

    public record Seed(boolean enabled, String demoPassword) {}
}
