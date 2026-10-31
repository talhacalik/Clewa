package org.calik.clewa.card.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardNumberGeneratorTest {

	@Test
	void generate_returnsSixteenDigitNumericString() {
		String cardNumber = CardNumberGenerator.generate();

		assertThat(cardNumber).matches("^[0-9]{16}$");
	}

	@RepeatedTest(20)
	void generate_alwaysReturnsValidFormat() {
		String cardNumber = CardNumberGenerator.generate();

		assertThat(cardNumber).hasSize(16);
		assertThat(cardNumber).matches("^[0-9]{16}$");
	}

}
