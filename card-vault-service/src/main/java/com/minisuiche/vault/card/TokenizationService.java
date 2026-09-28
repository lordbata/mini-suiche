package com.minisuiche.vault.card;

import com.minisuiche.vault.card.CardDtos.CardResponse;
import com.minisuiche.vault.card.CardDtos.TokenizeRequest;
import com.minisuiche.vault.crypto.PanCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenizationService {

    private static final Logger log = LoggerFactory.getLogger(TokenizationService.class);

    private final StoredCardRepository repository;
    private final PanCipher cipher;
    private final Clock clock;

    public TokenizationService(StoredCardRepository repository, PanCipher cipher, Clock clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional
    public CardResponse tokenize(TokenizeRequest req) {
        if (!CardNumbers.passesLuhn(req.pan())) {
            throw new InvalidCardException("El número de tarjeta no es válido");
        }
        if (YearMonth.of(req.expYear(), req.expMonth()).isBefore(YearMonth.now(clock))) {
            throw new InvalidCardException("La tarjeta está vencida");
        }
        // Aquí se enviaría el CVV al emisor para verificarlo (en la autorización).
        // Luego se descarta: no se guarda en ninguna parte.

        String hash = cipher.fingerprint(req.pan());
        Optional<StoredCard> existing = repository.findByPanHash(hash);
        if (existing.isPresent()) {
            // Misma tarjeta => mismo token (útil para antifraude y tarjetas guardadas)
            return CardResponse.from(existing.get());
        }

        StoredCard card = new StoredCard(
                "tok_" + UUID.randomUUID().toString().replace("-", ""),
                cipher.encrypt(req.pan()),
                hash,
                cipher.keyVersion(),
                req.pan().substring(req.pan().length() - 4),
                CardNumbers.brandOf(req.pan()),
                req.expMonth(),
                req.expYear());
        repository.save(card);

        // Log de auditoría SIN datos sensibles (PCI-DSS req. 10)
        log.info("Tarjeta tokenizada token={} brand={} pan={}", card.getToken(), card.getBrand(), CardNumbers.mask(req.pan()));
        return CardResponse.from(card);
    }

    @Transactional(readOnly = true)
    public CardResponse find(String token) {
        return repository.findById(token)
                .map(CardResponse::from)
                .orElseThrow(() -> new CardNotFoundException(token));
    }
}
