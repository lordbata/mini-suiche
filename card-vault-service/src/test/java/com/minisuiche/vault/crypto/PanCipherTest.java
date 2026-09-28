package com.minisuiche.vault.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanCipherTest {

    private final PanCipher cipher = new PanCipher(new VaultCryptoProperties(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "dGVzdC1obWFj", 1));

    @Test
    void cifraYDescifra() {
        String ct = cipher.encrypt("4111111111111111");
        assertThat(ct).doesNotContain("4111111111111111");
        assertThat(cipher.decrypt(ct)).isEqualTo("4111111111111111");
    }

    @Test
    void mismoPanProduceCifradosDistintosPorElIvAleatorio() {
        assertThat(cipher.encrypt("4111111111111111")).isNotEqualTo(cipher.encrypt("4111111111111111"));
    }

    @Test
    void laHuellaEsDeterministaParaPoderBuscar() {
        assertThat(cipher.fingerprint("4111111111111111")).isEqualTo(cipher.fingerprint("4111111111111111"));
    }
}
