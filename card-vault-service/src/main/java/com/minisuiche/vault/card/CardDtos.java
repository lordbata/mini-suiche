package com.minisuiche.vault.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Objetos de entrada/salida de la API (DTO). Nunca exponemos la entidad JPA directamente. */
public final class CardDtos {

    private CardDtos() {
    }

    public record TokenizeRequest(
            @NotBlank @Pattern(regexp = "\\d{13,19}", message = "PAN inválido") String pan,
            @Min(1) @Max(12) int expMonth,
            @Min(2000) @Max(2100) int expYear,
            @NotBlank @Pattern(regexp = "\\d{3,4}", message = "CVV inválido") String cvv) {

        /**
         * Los records generan toString() con TODOS los campos. Si alguien hace
         * log.info("{}", request) el PAN y el CVV terminarían en los logs
         * (violación de PCI-DSS req. 3.3 y 10). Por eso lo sobreescribimos.
         */
        @Override
        public String toString() {
            return "TokenizeRequest[pan=" + CardNumbers.mask(pan) + ", exp=" + expMonth + "/" + expYear + ", cvv=***]";
        }
    }

    /** Respuesta: el token y datos "no sensibles". El PAN jamás sale de la bóveda. */
    public record CardResponse(String token, String brand, String last4, int expMonth, int expYear) {

        static CardResponse from(StoredCard c) {
            return new CardResponse(c.getToken(), c.getBrand(), c.getLast4(), c.getExpMonth(), c.getExpYear());
        }
    }
}
