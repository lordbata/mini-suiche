package com.minisuiche.payment.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record CreatePaymentRequest(
            @NotBlank @Pattern(regexp = "tok_[a-f0-9]{32}", message = "token inválido") String cardToken,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "moneda ISO-4217, ej. VES o USD") String currency,
            @NotBlank String merchantId) {
    }

    public record PaymentResponse(String id, String status, String authCode, String declineReason,
                                  BigDecimal amount, String currency, String cardLast4, String merchantId,
                                  Instant createdAt) {

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getStatus().name(), p.getAuthCode(), p.getDeclineReason(),
                    p.getAmount(), p.getCurrency(), p.getCardLast4(), p.getMerchantId(), p.getCreatedAt());
        }
    }
}
