package org.calik.clewa.card.dto;

import java.math.BigDecimal;

import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardStatus;

public record CardResponse(String maskedCardNumber, CardStatus status, BigDecimal dailyLimit,
		BigDecimal remainingDailyLimit) {

	private static final String MASK_PREFIX = "**** **** **** ";
	private static final int VISIBLE_DIGITS = 4;

	// Kart numarasının her zaman 16 hane olduğunu varsayar; bunu DB'deki chk_cards_card_number_format kısıtı
	// garanti eder (bozuk bir numara zaten kaydedilemez).
	public static CardResponse from(Card card) {
		String cardNumber = card.getCardNumber();
		String lastDigits = cardNumber.substring(cardNumber.length() - VISIBLE_DIGITS);
		return new CardResponse(MASK_PREFIX + lastDigits, card.getStatus(), card.getDailyLimit(),
				card.getRemainingDailyLimit());
	}

}
