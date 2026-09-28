package com.minisuiche.payment.payment;

import com.minisuiche.payment.payment.PaymentDtos.CreatePaymentRequest;
import com.minisuiche.payment.payment.PaymentDtos.PaymentResponse;
import com.minisuiche.payment.vault.CardVaultClient;
import com.minisuiche.payment.vault.CardVaultClient.CardSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final CardVaultClient cardVault;
    private final Clock clock;
    private final BigDecimal maxAmount;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(PaymentRepository repository, CardVaultClient cardVault, Clock clock,
                          @Value("${payments.max-amount}") BigDecimal maxAmount) {
        this.repository = repository;
        this.cardVault = cardVault;
        this.clock = clock;
        this.maxAmount = maxAmount;
    }

    /**
     * IDEMPOTENCIA: en pagos, un reintento por timeout de red NO puede cobrar dos veces.
     * El cliente envía un Idempotency-Key único por intento de pago; si ya lo
     * procesamos, devolvemos el mismo resultado sin volver a cobrar.
     *
     * @return el pago y si fue creado ahora (true) o era un reintento (false)
     */
    public Result process(String idempotencyKey, CreatePaymentRequest req) {
        Optional<Payment> previous = repository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            return new Result(PaymentResponse.from(previous.get()), false);
        }

        CardSummary card = cardVault.findCard(req.cardToken());
        Payment payment = authorize(idempotencyKey, req, card);

        try {
            repository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones con la misma llave llegaron a la vez: gana la primera
            // (la restricción UNIQUE de la BD es la garantía final).
            return new Result(PaymentResponse.from(repository.findByIdempotencyKey(idempotencyKey).orElseThrow()), false);
        }

        log.info("Pago {} {} monto={} {} tarjeta=****{} comercio={}",
                payment.getId(), payment.getStatus(), payment.getAmount(), payment.getCurrency(),
                payment.getCardLast4(), payment.getMerchantId());
        return new Result(PaymentResponse.from(payment), true);
    }

    public Optional<PaymentResponse> find(String id) {
        return repository.findById(id).map(PaymentResponse::from);
    }

    /** Reglas de autorización simuladas. En la vida real aquí se consulta al emisor vía ISO 8583. */
    private Payment authorize(String idempotencyKey, CreatePaymentRequest req, CardSummary card) {
        Payment.Status status = Payment.Status.APPROVED;
        String reason = null;
        String authCode = null;

        if (YearMonth.of(card.expYear(), card.expMonth()).isBefore(YearMonth.now(clock))) {
            status = Payment.Status.DECLINED;
            reason = "EXPIRED_CARD";
        } else if (req.amount().compareTo(maxAmount) > 0) {
            status = Payment.Status.DECLINED;
            reason = "LIMIT_EXCEEDED";
        } else {
            authCode = String.format("%06d", random.nextInt(1_000_000));
        }

        return new Payment(UUID.randomUUID().toString(), idempotencyKey, req.merchantId(), card.token(),
                card.last4(), req.amount(), req.currency(), status, authCode, reason,
                Instant.now(clock));
    }

    public record Result(PaymentResponse payment, boolean created) {
    }
}
