package com.minisuiche.vault.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Criptografía del PAN.
 *
 * PCI-DSS req. 3.5: el PAN almacenado debe ser ilegible. Aquí usamos:
 *  - AES-256-GCM para CIFRAR (reversible, solo con la clave). GCM además
 *    detecta si alguien alteró el texto cifrado (autenticación).
 *  - HMAC-SHA256 para obtener una HUELLA (irreversible) que permite buscar
 *    "¿ya tengo esta tarjeta?" sin descifrar nada. Un hash simple (SHA-256 sin
 *    clave) NO sirve: con solo 10^15 PAN posibles se podría romper por fuerza bruta.
 */
@Component
public class PanCipher {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;       // tamaño recomendado para GCM
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final int keyVersion;
    private final SecureRandom random = new SecureRandom();

    public PanCipher(VaultCryptoProperties props) {
        byte[] aes = Base64.getDecoder().decode(props.encryptionKey());
        if (aes.length != 32) {
            throw new IllegalStateException("vault.crypto.encryption-key debe ser de 32 bytes (AES-256)");
        }
        this.aesKey = new SecretKeySpec(aes, "AES");
        this.hmacKey = new SecretKeySpec(Base64.getDecoder().decode(props.hmacKey()), "HmacSHA256");
        this.keyVersion = props.keyVersion();
    }

    /** Devuelve Base64( IV || textoCifrado+tag ). Cada llamada usa un IV aleatorio nuevo. */
    public String encrypt(String pan) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(pan.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error cifrando PAN", e);
        }
    }

    /** Solo lo usaría el conector hacia la red de tarjetas (Visa/Mastercard). No se expone por API. */
    public String decrypt(String payload) {
        try {
            byte[] all = Base64.getDecoder().decode(payload);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] pt = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(pt, StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error descifrando PAN", e);
        }
    }

    public String fingerprint(String pan) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(pan.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error calculando huella", e);
        }
    }

    public int keyVersion() {
        return keyVersion;
    }
}
