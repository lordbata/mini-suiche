package com.minisuiche.vault.card;

/**
 * Utilidades sobre números de tarjeta: validación Luhn, marca y enmascarado.
 */
public final class CardNumbers {

    private CardNumbers() {
    }

    /**
     * Algoritmo de Luhn (mod 10): detecta errores de tipeo en el PAN.
     * Recorre de derecha a izquierda duplicando cada segundo dígito.
     */
    public static boolean passesLuhn(String pan) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int d = pan.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Marca según el BIN (primeros dígitos). Simplificado para el ejemplo. */
    public static String brandOf(String pan) {
        if (pan.startsWith("4")) {
            return "VISA";
        }
        int two = Integer.parseInt(pan.substring(0, 2));
        int four = Integer.parseInt(pan.substring(0, 4));
        if ((two >= 51 && two <= 55) || (four >= 2221 && four <= 2720)) {
            return "MASTERCARD";
        }
        if (two == 34 || two == 37) {
            return "AMEX";
        }
        return "OTHER";
    }

    /**
     * PCI-DSS req. 3.4.1: el PAN se muestra enmascarado. Como máximo se permiten
     * los primeros 6 y los últimos 4 dígitos; aquí mostramos solo los últimos 4.
     */
    public static String mask(String pan) {
        if (pan == null || pan.length() < 4) {
            return "****";
        }
        return "*".repeat(pan.length() - 4) + pan.substring(pan.length() - 4);
    }
}
