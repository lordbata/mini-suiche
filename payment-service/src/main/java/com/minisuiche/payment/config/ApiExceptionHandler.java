package com.minisuiche.payment.config;

import com.minisuiche.payment.vault.CardVaultUnavailableException;
import com.minisuiche.payment.vault.UnknownCardTokenException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fields);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class,
            MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    ProblemDetail badRequest(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                ex instanceof MissingRequestHeaderException m
                        ? "Falta el header " + m.getHeaderName()
                        : "Petición inválida");
    }

    @ExceptionHandler(UnknownCardTokenException.class)
    ProblemDetail unknownToken(UnknownCardTokenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(CardVaultUnavailableException.class)
    ProblemDetail vaultDown(CardVaultUnavailableException ex) {
        log.error("Bóveda no disponible", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Servicio temporalmente no disponible, reintente con la misma Idempotency-Key");
    }
}
