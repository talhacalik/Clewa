package org.calik.clewa.card.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardTransaction;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {

	// Kasıtlı olarak sadece idempotencyKey ile değil, kartla birlikte aranır (başka kartın kaydı sızmasın).
	Optional<CardTransaction> findByCardAndIdempotencyKey(Card card, String idempotencyKey);

}
