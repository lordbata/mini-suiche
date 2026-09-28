package com.minisuiche.payment.vault;

public class UnknownCardTokenException extends RuntimeException {

    public UnknownCardTokenException(String token) {
        super("Token de tarjeta desconocido: " + token);
    }
}
