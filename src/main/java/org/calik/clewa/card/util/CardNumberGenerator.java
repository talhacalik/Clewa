package org.calik.clewa.card.util;

import java.security.SecureRandom;

import org.calik.clewa.card.entity.Card;

public final class CardNumberGenerator {

	private static final SecureRandom RANDOM = new SecureRandom();

	private CardNumberGenerator() {
	}

	public static String generate() {
		StringBuilder cardNumber = new StringBuilder(Card.CARD_NUMBER_LENGTH);
		for (int i = 0; i < Card.CARD_NUMBER_LENGTH; i++) {
			cardNumber.append(RANDOM.nextInt(10));
		}
		return cardNumber.toString();
	}

}
