package org.calik.clewa.wallet.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberGeneratorTest {

	@Test
	void generate_returnsTenDigitNumericString() {
		String accountNumber = AccountNumberGenerator.generate();

		assertThat(accountNumber).matches("^[0-9]{10}$");
	}

	@RepeatedTest(20)
	void generate_alwaysReturnsValidFormat() {
		String accountNumber = AccountNumberGenerator.generate();

		assertThat(accountNumber).hasSize(10);
		assertThat(accountNumber).matches("^[0-9]{10}$");
	}

}
