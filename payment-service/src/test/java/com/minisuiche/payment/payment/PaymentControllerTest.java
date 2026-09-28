package com.minisuiche.payment.payment;

import com.minisuiche.payment.vault.CardVaultClient;
import com.minisuiche.payment.vault.CardVaultClient.CardSummary;
import com.minisuiche.payment.vault.CardVaultUnavailableException;
import com.minisuiche.payment.vault.UnknownCardTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Levanta payment-service con H2; la bóveda se simula con un mock. */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    private static final String TOKEN = "tok_0123456789abcdef0123456789abcdef";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CardVaultClient cardVault;

    private String body(String amount) {
        return """
                {"cardToken":"%s","amount":%s,"currency":"USD","merchantId":"COMERCIO-01"}
                """.formatted(TOKEN, amount);
    }

    @Test
    void apruebaPagoYElReintentoNoCobraDosVeces() throws Exception {
        when(cardVault.findCard(TOKEN)).thenReturn(new CardSummary(TOKEN, "VISA", "1111", 12, 2099));

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "pedido-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body("150.75")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.cardLast4").value("1111"));

        // Mismo Idempotency-Key => 200 con el mismo pago, sin volver a llamar a la bóveda
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "pedido-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body("150.75")))
                .andExpect(status().isOk());
        verify(cardVault, times(1)).findCard(anyString());
    }

    @Test
    void rechazaMontoSobreElLimite() throws Exception {
        when(cardVault.findCard(TOKEN)).thenReturn(new CardSummary(TOKEN, "VISA", "1111", 12, 2099));

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "pedido-0002")
                        .contentType(MediaType.APPLICATION_JSON).content(body("9999.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("LIMIT_EXCEEDED"));
    }

    @Test
    void tokenDesconocidoDa422() throws Exception {
        when(cardVault.findCard(TOKEN)).thenThrow(new UnknownCardTokenException(TOKEN));

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "pedido-0003")
                        .contentType(MediaType.APPLICATION_JSON).content(body("10")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void bovedaCaidaDa503() throws Exception {
        when(cardVault.findCard(TOKEN)).thenThrow(new CardVaultUnavailableException(new RuntimeException("timeout")));

        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "pedido-0004")
                        .contentType(MediaType.APPLICATION_JSON).content(body("10")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void sinIdempotencyKeyDa400() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON).content(body("10")))
                .andExpect(status().isBadRequest());
    }
}
