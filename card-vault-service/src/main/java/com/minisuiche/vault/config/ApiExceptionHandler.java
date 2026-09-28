package com.minisuiche.vault.config;

import com.minisuiche.vault.card.CardNotFoundException;
import com.minisuiche.vault.card.InvalidCardException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Convierte excepciones en respuestas de error estándar (RFC 9457 "Problem Details").
 * Importante para PCI: los mensajes de error NUNCA repiten el valor recibido
 * (ej. "PAN 4111... inválido"), solo el nombre del campo.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Cuerpo de la petición inválido");
    }

    @ExceptionHandler(InvalidCardException.class)
    ProblemDetail invalidCard(InvalidCardException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(CardNotFoundException.class)
    ProblemDetail notFound(CardNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
