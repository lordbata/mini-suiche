package com.minisuiche.vault.card;

import com.minisuiche.vault.card.CardDtos.CardResponse;
import com.minisuiche.vault.card.CardDtos.TokenizeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de la bóveda.
 *  - /api/v1/...      => pública, se publica a través de WSO2 API Manager.
 *  - /internal/v1/... => solo para otros microservicios dentro del clúster
 *                        (la NetworkPolicy de Kubernetes y WSO2 no la exponen).
 */
@RestController
public class CardController {

    private final TokenizationService service;

    public CardController(TokenizationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/cards/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse tokenize(@Valid @RequestBody TokenizeRequest request) {
        return service.tokenize(request);
    }

    @GetMapping("/internal/v1/cards/{token}")
    public CardResponse find(@PathVariable String token) {
        return service.find(token);
    }
}
