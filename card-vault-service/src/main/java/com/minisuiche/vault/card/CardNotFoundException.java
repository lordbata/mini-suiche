package com.minisuiche.vault.card;

public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(String token) {
        super("Token no encontrado: " + token);
    }
}
