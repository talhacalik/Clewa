package org.calik.clewa.card.dto;

import org.junit.jupiter.api.Test;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardStatus;
import org.calik.clewa.wallet.entity.Wallet;

import static org.assertj.core.api.Assertions.assertThat;

class CardResponseTest {

	private Card buildCard(String cardNumber) {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		return new Card(new Wallet(user, "1111111111"), cardNumber);
	}

	@Test
	void from_masksCardNumberExceptLastFourDigits() {
		CardResponse response = CardResponse.from(buildCard("1234567890123456"));

		assertThat(response.maskedCardNumber()).isEqualTo("**** **** **** 3456");
	}

	@Test
	void from_neverContainsTheFullCardNumber() {
		CardResponse response = CardResponse.from(buildCard("1234567890123456"));

		assertThat(response.toString()).doesNotContain("1234567890123456");
		assertThat(response.toString()).doesNotContain("5678");
	}

	@Test
	void from_copiesStatusAndLimits() {
		CardResponse response = CardResponse.from(buildCard("1234567890123456"));

		assertThat(response.status()).isEqualTo(CardStatus.ACTIVE);
		assertThat(response.dailyLimit()).isEqualByComparingTo("500000.00");
		assertThat(response.remainingDailyLimit()).isEqualByComparingTo("500000.00");
	}

}
