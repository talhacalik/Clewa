package org.calik.clewa.card.dto;

import java.math.BigDecimal;

import org.calik.clewa.card.entity.Card;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateDailyLimitRequest(

		// integer = 17, fraction = 2: veritabanındaki NUMERIC(19,2) ile uyumlu; kuruş altı ve dev üslü sayıları reddeder.
		@NotNull(message = "{card.dailyLimit.required}")
		@Positive(message = "{card.dailyLimit.positive}")
		@Digits(integer = 17, fraction = 2, message = "{card.dailyLimit.digits}")
		@DecimalMax(value = Card.MAX_DAILY_LIMIT_VALUE, message = "{card.dailyLimit.max}")
		BigDecimal dailyLimit

) {
}
