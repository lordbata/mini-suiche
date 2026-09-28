package com.minisuiche.vault.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tarjeta guardada en la bóveda. Observa lo que SÍ y lo que NO se guarda:
 *  - SÍ: PAN cifrado, huella HMAC, últimos 4, marca, vencimiento.
 *  - NO: el CVV. PCI-DSS req. 3.3.1 prohíbe guardar datos sensibles de
 *    autenticación (CVV, PIN, banda magnética) después de autorizar,
 *    aunque estén cifrados.
 */
@Entity
@Table(name = "CARDS")
public class StoredCard {

    @Id
    @Column(name = "TOKEN", length = 40)
    private String token;

    @Column(name = "PAN_CIPHERTEXT", nullable = false, length = 512)
    private String panCiphertext;

    @Column(name = "PAN_HASH", nullable = false, length = 64, unique = true)
    private String panHash;

    @Column(name = "KEY_VERSION", nullable = false)
    private int keyVersion;

    @Column(name = "LAST4", nullable = false, length = 4)
    private String last4;

    @Column(name = "BRAND", nullable = false, length = 20)
    private String brand;

    @Column(name = "EXP_MONTH", nullable = false)
    private int expMonth;

    @Column(name = "EXP_YEAR", nullable = false)
    private int expYear;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    protected StoredCard() {
        // requerido por JPA
    }

    public StoredCard(String token, String panCiphertext, String panHash, int keyVersion,
                      String last4, String brand, int expMonth, int expYear) {
        this.token = token;
        this.panCiphertext = panCiphertext;
        this.panHash = panHash;
        this.keyVersion = keyVersion;
        this.last4 = last4;
        this.brand = brand;
        this.expMonth = expMonth;
        this.expYear = expYear;
        this.createdAt = Instant.now();
    }

    public String getToken() { return token; }
    public String getPanCiphertext() { return panCiphertext; }
    public String getPanHash() { return panHash; }
    public int getKeyVersion() { return keyVersion; }
    public String getLast4() { return last4; }
    public String getBrand() { return brand; }
    public int getExpMonth() { return expMonth; }
    public int getExpYear() { return expYear; }
    public Instant getCreatedAt() { return createdAt; }
}
