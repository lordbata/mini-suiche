package com.minisuiche.vault.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data genera la implementación (SQL) a partir del nombre del método. */
public interface StoredCardRepository extends JpaRepository<StoredCard, String> {

    Optional<StoredCard> findByPanHash(String panHash);
}
