package org.calik.clewa.card.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.calik.clewa.card.entity.CardTransaction;

public record SpendRequest(

		// integer = 17, fraction = 2: NUMERIC(19,2) ile uyumlu; kuruş altı tutar ve dev üslü sayıları kapıda reddeder.
		@NotNull(message = "{card.spend.amount.required}")
		@Positive(message = "{card.spend.amount.positive}")
		@Digits(integer = 17, fraction = 2, message = "{card.spend.amount.digits}")
		BigDecimal amount,

		@NotBlank(message = "{card.spend.merchantName.required}")
		@Size(max = CardTransaction.MERCHANT_NAME_MAX_LENGTH, message = "{card.spend.merchantName.size}")
		String merchantName

) {
}
