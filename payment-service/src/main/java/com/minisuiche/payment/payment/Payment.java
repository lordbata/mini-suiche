package com.minisuiche.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una transacción de pago. Montos SIEMPRE en BigDecimal (nunca double:
 * 0.1 + 0.2 != 0.3 en punto flotante) y en Oracle como NUMBER(19,2).
 */
@Entity
@Table(name = "PAYMENTS")
public class Payment {

    public enum Status { APPROVED, DECLINED }

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "IDEMPOTENCY_KEY", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "MERCHANT_ID", nullable = false, length = 40)
    private String merchantId;

    @Column(name = "CARD_TOKEN", nullable = false, length = 40)
    private String cardToken;

    @Column(name = "CARD_LAST4", nullable = false, length = 4)
    private String cardLast4;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private Status status;

    @Column(name = "AUTH_CODE", length = 6)
    private String authCode;

    @Column(name = "DECLINE_REASON", length = 40)
    private String declineReason;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(String id, String idempotencyKey, String merchantId, String cardToken, String cardLast4,
                   BigDecimal amount, String currency, Status status, String authCode, String declineReason,
                   Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.merchantId = merchantId;
        this.cardToken = cardToken;
        this.cardLast4 = cardLast4;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.authCode = authCode;
        this.declineReason = declineReason;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMerchantId() { return merchantId; }
    public String getCardToken() { return cardToken; }
    public String getCardLast4() { return cardLast4; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public String getAuthCode() { return authCode; }
    public String getDeclineReason() { return declineReason; }
    public Instant getCreatedAt() { return createdAt; }
}
