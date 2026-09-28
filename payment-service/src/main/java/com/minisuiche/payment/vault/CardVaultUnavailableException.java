package com.minisuiche.payment.vault;

public class CardVaultUnavailableException extends RuntimeException {

    public CardVaultUnavailableException(Throwable cause) {
        super("card-vault-service no disponible", cause);
    }
}
