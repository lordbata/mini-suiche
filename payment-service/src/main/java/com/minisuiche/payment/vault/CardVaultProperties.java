package com.minisuiche.payment.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Dónde está la bóveda. En Kubernetes es el nombre del Service
 * (http://card-vault-service:8081): el DNS interno del clúster lo resuelve.
 */
@ConfigurationProperties(prefix = "card-vault")
public record CardVaultProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
