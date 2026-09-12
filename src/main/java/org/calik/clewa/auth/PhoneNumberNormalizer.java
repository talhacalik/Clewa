package org.calik.clewa.auth;

import org.calik.clewa.auth.exception.InvalidPhoneNumberException;

public final class PhoneNumberNormalizer {

	private PhoneNumberNormalizer() {
	}

	public static String normalize(String rawPhoneNumber) {
		if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
			throw new InvalidPhoneNumberException("Geçersiz telefon numarası formatı.");
		}
		if (rawPhoneNumber.matches("^0[0-9]{10}$")) {
			return "+90" + rawPhoneNumber.substring(1);
		}
		if (rawPhoneNumber.matches("^[0-9]{10}$")) {
			return "+90" + rawPhoneNumber;
		}
		throw new InvalidPhoneNumberException("Geçersiz telefon numarası formatı.");
	}

}
