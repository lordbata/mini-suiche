package com.minisuiche.payment.vault;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP hacia card-vault-service (comunicación síncrona entre microservicios).
 *
 * Buenas prácticas aplicadas:
 *  - Timeouts explícitos: si la bóveda se cuelga, el pago falla rápido en vez
 *    de bloquear hilos (evita fallos en cascada).
 *  - Errores traducidos a excepciones de negocio (token inválido vs. servicio caído).
 */
@Component
public class CardVaultClient {

    private final RestClient restClient;

    public CardVaultClient(RestClient.Builder builder, CardVaultProperties props) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.connectTimeout());
        requestFactory.setReadTimeout(props.readTimeout());
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public CardSummary findCard(String token) {
        try {
            return restClient.get()
                    .uri("/internal/v1/cards/{token}", token)
                    .retrieve()
                    .body(CardSummary.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new UnknownCardTokenException(token);
            }
            throw new CardVaultUnavailableException(e);
        } catch (ResourceAccessException e) {        // timeout o conexión rechazada
            throw new CardVaultUnavailableException(e);
        } catch (RestClientException e) {            // 5xx u otros
            throw new CardVaultUnavailableException(e);
        }
    }

    public record CardSummary(String token, String brand, String last4, int expMonth, int expYear) {
    }
}
