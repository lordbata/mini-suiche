package com.minisuiche.vault.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Claves criptográficas de la bóveda.
 *
 * PCI-DSS req. 3.6 / 3.7: las claves NUNCA van en el código ni en Git.
 * - En local: vienen de application.yml (valores de prueba).
 * - En Kubernetes: de un Secret.
 * - En Azure: de Azure Key Vault, montado en el pod con el CSI Secrets Store Driver.
 *
 * @param encryptionKey clave AES-256 en Base64 (32 bytes) para cifrar el PAN
 * @param hmacKey       clave HMAC-SHA256 en Base64 para calcular la "huella" del PAN
 * @param keyVersion    versión de la clave activa (permite rotación de claves)
 */
@Validated
@ConfigurationProperties(prefix = "vault.crypto")
public record VaultCryptoProperties(
        @NotBlank String encryptionKey,
        @NotBlank String hmacKey,
        int keyVersion) {
}
