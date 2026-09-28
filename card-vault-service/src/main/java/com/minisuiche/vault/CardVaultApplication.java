package com.minisuiche.vault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Card Vault: el ÚNICO microservicio que ve el número de tarjeta (PAN).
 *
 * Idea clave de PCI-DSS: reducir el "alcance" (scope). Si solo este servicio
 * toca el PAN, solo él (y su base de datos) debe cumplir los controles más
 * estrictos. El resto del sistema trabaja con un token sin valor para un atacante.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CardVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardVaultApplication.class, args);
    }
}
