package org.calik.clewa.wallet.entity;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.wallet.exception.InsufficientBalanceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

	private Wallet walletWithBalance(String balance) {
		Wallet wallet = new Wallet(new User(), "1234567890");
		wallet.setBalance(new BigDecimal(balance));
		return wallet;
	}

	@Test
	void credit_addsAmountToBalance() {
		Wallet wallet = walletWithBalance("100.00");

		wallet.credit(new BigDecimal("50.00"));

		assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.00", "-1.00" })
	void credit_whenAmountIsZeroOrNegative_throws(String amount) {
		Wallet wallet = walletWithBalance("100.00");

		assertThatThrownBy(() -> wallet.credit(new BigDecimal(amount)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void debit_subtractsAmountFromBalance() {
		Wallet wallet = walletWithBalance("100.00");

		wallet.debit(new BigDecimal("30.00"));

		assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
	}

	@Test
	void debit_whenAmountEqualsBalance_resultsInZero() {
		Wallet wallet = walletWithBalance("100.00");

		wallet.debit(new BigDecimal("100.00"));

		assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void debit_whenAmountExceedsBalance_throwsInsufficientBalanceAndLeavesBalanceUnchanged() {
		Wallet wallet = walletWithBalance("50.00");

		assertThatThrownBy(() -> wallet.debit(new BigDecimal("50.01")))
			.isInstanceOf(InsufficientBalanceException.class);

		assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("50.00"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.00", "-1.00" })
	void debit_whenAmountIsZeroOrNegative_throws(String amount) {
		Wallet wallet = walletWithBalance("100.00");

		assertThatThrownBy(() -> wallet.debit(new BigDecimal(amount)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void credit_whenAmountIsNull_throwsNullPointerException() {
		Wallet wallet = walletWithBalance("100.00");

		assertThatThrownBy(() -> wallet.credit(null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void debit_whenAmountIsNull_throwsNullPointerException() {
		Wallet wallet = walletWithBalance("100.00");

		assertThatThrownBy(() -> wallet.debit(null))
			.isInstanceOf(NullPointerException.class);
	}

}
