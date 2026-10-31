package org.calik.clewa.card.entity;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.card.exception.CardNotActiveException;
import org.calik.clewa.card.exception.DailyLimitExceededException;
import org.calik.clewa.wallet.entity.Wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTest {

	private Card buildCard() {
		return new Card(new Wallet(new User(), "1111111111"), "1234567890123456");
	}

	@Test
	void freeze_setsStatusToFrozen() {
		Card card = buildCard();

		card.freeze();

		assertThat(card.getStatus()).isEqualTo(CardStatus.FROZEN);
	}

	@Test
	void freeze_whenAlreadyFrozen_staysFrozen() {
		Card card = buildCard();
		card.freeze();

		card.freeze();

		assertThat(card.getStatus()).isEqualTo(CardStatus.FROZEN);
	}

	@Test
	void unfreeze_setsStatusBackToActive() {
		Card card = buildCard();
		card.freeze();

		card.unfreeze();

		assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
	}

	@Test
	void changeDailyLimit_changesOnlyTheLimitNotTodaysRemaining() {
		Card card = buildCard();

		card.changeDailyLimit(new BigDecimal("1000"));

		assertThat(card.getDailyLimit()).isEqualByComparingTo("1000.00");
		assertThat(card.getDailyLimit().scale()).isEqualTo(2);
		assertThat(card.getRemainingDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-1", "-0.01" })
	void changeDailyLimit_rejectsNonPositiveLimit(String invalidLimit) {
		Card card = buildCard();

		assertThatThrownBy(() -> card.changeDailyLimit(new BigDecimal(invalidLimit)))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(card.getDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@Test
	void changeDailyLimit_acceptsExactlyTheMaximum() {
		Card card = buildCard();

		card.changeDailyLimit(Card.MAX_DAILY_LIMIT);

		assertThat(card.getDailyLimit()).isEqualByComparingTo(Card.MAX_DAILY_LIMIT);
	}

	@Test
	void changeDailyLimit_rejectsAboveMaximum() {
		Card card = buildCard();

		assertThatThrownBy(() -> card.changeDailyLimit(Card.MAX_DAILY_LIMIT.add(new BigDecimal("0.01"))))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(card.getDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@ParameterizedTest
	@ValueSource(strings = { "10.005", "1000.001", "0.015" })
	void changeDailyLimit_rejectsSubCentAmountsInsteadOfRoundingSilently(String subCentLimit) {
		Card card = buildCard();

		assertThatThrownBy(() -> card.changeDailyLimit(new BigDecimal(subCentLimit)))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(card.getDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@Test
	void changeDailyLimit_acceptsTrailingZerosBeyondTwoDecimals() {
		Card card = buildCard();

		card.changeDailyLimit(new BigDecimal("1000.5000"));

		assertThat(card.getDailyLimit()).isEqualByComparingTo("1000.50");
		assertThat(card.getDailyLimit().scale()).isEqualTo(2);
	}

	@Test
	void spend_reducesOnlyRemainingDailyLimit() {
		Card card = buildCard();

		card.spend(new BigDecimal("100.50"));

		assertThat(card.getRemainingDailyLimit()).isEqualByComparingTo("499899.50");
		assertThat(card.getRemainingDailyLimit().scale()).isEqualTo(2);
		assertThat(card.getDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@Test
	void spend_whenFrozen_throwsCardNotActive() {
		Card card = buildCard();
		card.freeze();

		assertThatThrownBy(() -> card.spend(new BigDecimal("1.00")))
			.isInstanceOf(CardNotActiveException.class);

		assertThat(card.getRemainingDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@Test
	void spend_aboveRemainingLimit_throwsDailyLimitExceeded() {
		Card card = buildCard();

		assertThatThrownBy(() -> card.spend(new BigDecimal("500000.01")))
			.isInstanceOf(DailyLimitExceededException.class);

		assertThat(card.getRemainingDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1.00", "10.005" })
	void spend_rejectsNonPositiveOrSubCentAmounts(String invalidAmount) {
		Card card = buildCard();

		assertThatThrownBy(() -> card.spend(new BigDecimal(invalidAmount)))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(card.getRemainingDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@Test
	void changeDailyLimit_rejectsNull() {
		assertThatThrownBy(() -> buildCard().changeDailyLimit(null)).isInstanceOf(NullPointerException.class);
	}

}
