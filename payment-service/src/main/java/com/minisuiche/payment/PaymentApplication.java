package com.minisuiche.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Payment Service: procesa pagos. NUNCA recibe el número de tarjeta,
 * solo el token emitido por card-vault-service. Así queda fuera del
 * alcance más estricto de PCI-DSS.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
