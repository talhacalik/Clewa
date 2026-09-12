package org.calik.clewa.auth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import org.calik.clewa.auth.exception.InvalidPhoneNumberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberNormalizerTest {

	@ParameterizedTest(name = "\"{0}\" should normalize to \"{1}\"")
	@CsvSource({
		"5321234567, +905321234567",
		"05321234567, +905321234567"
	})
	void normalize_acceptsKnownFormats(String rawInput, String expected) {
		assertThat(PhoneNumberNormalizer.normalize(rawInput)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "\"{0}\" should be rejected")
	@ValueSource(strings = { "123", "53212345678", "abcdefghij", "+905321234567" })
	void normalize_rejectsUnknownFormats(String rawInput) {
		assertThatThrownBy(() -> PhoneNumberNormalizer.normalize(rawInput))
			.isInstanceOf(InvalidPhoneNumberException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void normalize_rejectsNullOrBlankInput(String rawInput) {
		assertThatThrownBy(() -> PhoneNumberNormalizer.normalize(rawInput))
			.isInstanceOf(InvalidPhoneNumberException.class);
	}

}
