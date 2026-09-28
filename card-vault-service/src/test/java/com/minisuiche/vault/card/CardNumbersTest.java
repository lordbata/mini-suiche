package com.minisuiche.vault.card;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardNumbersTest {

    @Test
    void luhnAceptaTarjetasDePruebaValidas() {
        assertThat(CardNumbers.passesLuhn("4111111111111111")).isTrue();
        assertThat(CardNumbers.passesLuhn("5555555555554444")).isTrue();
    }

    @Test
    void luhnRechazaErroresDeTipeo() {
        assertThat(CardNumbers.passesLuhn("4111111111111112")).isFalse();
    }

    @Test
    void detectaMarca() {
        assertThat(CardNumbers.brandOf("4111111111111111")).isEqualTo("VISA");
        assertThat(CardNumbers.brandOf("5555555555554444")).isEqualTo("MASTERCARD");
        assertThat(CardNumbers.brandOf("378282246310005")).isEqualTo("AMEX");
    }

    @Test
    void enmascaraDejandoSoloLosUltimosCuatro() {
        assertThat(CardNumbers.mask("4111111111111111")).isEqualTo("************1111");
    }
}
