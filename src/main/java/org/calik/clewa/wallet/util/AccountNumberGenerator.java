package org.calik.clewa.wallet.util;

import java.security.SecureRandom;

public final class AccountNumberGenerator {

	public static final int ACCOUNT_NUMBER_LENGTH = 10;
	private static final SecureRandom RANDOM = new SecureRandom();

	private AccountNumberGenerator() {
	}

	public static String generate() {
		StringBuilder accountNumber = new StringBuilder(ACCOUNT_NUMBER_LENGTH);
		for (int i = 0; i < ACCOUNT_NUMBER_LENGTH; i++) {
			accountNumber.append(RANDOM.nextInt(10));
		}
		return accountNumber.toString();
	}

}
