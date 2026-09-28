package com.minisuiche.payment.payment;

import com.minisuiche.payment.payment.PaymentDtos.CreatePaymentRequest;
import com.minisuiche.payment.payment.PaymentDtos.PaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** 201 si el pago es nuevo, 200 si es un reintento con la misma Idempotency-Key. */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentService.Result result = service.process(idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> get(@PathVariable String id) {
        return ResponseEntity.of(service.find(id));
    }
}
