package org.calik.clewa.card.dto;

import java.math.BigDecimal;
import java.time.Instant;

import org.calik.clewa.card.entity.CardTransaction;

// Dekont: bakiye, limit ya da kart bilgisi içermez; sadece işlemin kendisi.
public record SpendResponse(BigDecimal amount, String merchantName, Instant createdAt) {

	public static SpendResponse from(CardTransaction transaction) {
		return new SpendResponse(transaction.getAmount(), transaction.getMerchantName(), transaction.getCreatedAt());
	}

}
