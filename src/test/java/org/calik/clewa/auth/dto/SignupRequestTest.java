package org.calik.clewa.auth.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

class SignupRequestTest {

	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("messages");
		messageSource.setDefaultEncoding("UTF-8");

		validator = new LocalValidatorFactoryBean();
		validator.setValidationMessageSource(messageSource);
		validator.afterPropertiesSet();
	}

	private SignupRequest validRequest() {
		return new SignupRequest("+905321234567", "Password1", "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));
	}

	@Test
	void validRequest_hasNoViolations() {
		assertThat(validator.validate(validRequest())).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void blankPhoneNumber_isRejected(String phoneNumber) {
		SignupRequest request = new SignupRequest(phoneNumber, "Password1", "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));

		assertThat(hasViolationOn(request, "phoneNumber")).isTrue();
	}

	@Test
	void blankPhoneNumber_hasTurkishErrorMessage() {
		SignupRequest request = new SignupRequest("", "Password1", "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));

		assertThat(violationMessageOn(request, "phoneNumber")).isEqualTo("Telefon numarası zorunludur.");
	}

	@ParameterizedTest(name = "password \"{0}\" should be rejected")
	@ValueSource(strings = { "", "1234567", "12345678", "abcdefgh", "Pass1" })
	void invalidPassword_isRejected(String password) {
		SignupRequest request = new SignupRequest("+905321234567", password, "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));

		assertThat(hasViolationOn(request, "password")).isTrue();
	}

	@ParameterizedTest(name = "password \"{0}\" should be accepted")
	@ValueSource(strings = { "Password1", "PASSWORD1a", "abcd1234" })
	void validPassword_isAccepted(String password) {
		SignupRequest request = new SignupRequest("+905321234567", password, "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));

		assertThat(hasViolationOn(request, "password")).isFalse();
	}

	@ParameterizedTest(name = "name of length {0} should be rejected")
	@ValueSource(ints = { 0, 1, 51 })
	void firstName_outsideSizeLimits_isRejected(int length) {
		SignupRequest request = new SignupRequest("+905321234567", "Password1", "a".repeat(length), "Yılmaz", LocalDate.of(1995, 3, 20));

		assertThat(hasViolationOn(request, "firstName")).isTrue();
	}

	@ParameterizedTest(name = "name of length {0} should be accepted")
	@ValueSource(ints = { 2, 50 })
	void firstName_withinSizeLimits_isAccepted(int length) {
		SignupRequest request = new SignupRequest("+905321234567", "Password1", "a".repeat(length), "Yılmaz", LocalDate.of(1995, 3, 20));

		assertThat(hasViolationOn(request, "firstName")).isFalse();
	}

	@Test
	void nullDateOfBirth_isRejected() {
		SignupRequest request = new SignupRequest("+905321234567", "Password1", "Ahmet", "Yılmaz", null);

		assertThat(hasViolationOn(request, "dateOfBirth")).isTrue();
	}

	@Test
	void futureDateOfBirth_isRejected() {
		SignupRequest request = new SignupRequest("+905321234567", "Password1", "Ahmet", "Yılmaz", LocalDate.now().plusDays(1));

		assertThat(hasViolationOn(request, "dateOfBirth")).isTrue();
	}

	@Test
	void password_exactlyMaxLength_isAccepted() {
		SignupRequest request = new SignupRequest("+905321234567", "Aa1".repeat(24), "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));

		assertThat(hasViolationOn(request, "password")).isFalse();
	}

	@Test
	void password_exceedingMaxLength_isRejected() {
		SignupRequest request = new SignupRequest("+905321234567", "Aa1".repeat(24) + "a", "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));

		assertThat(hasViolationOn(request, "password")).isTrue();
	}

	private boolean hasViolationOn(SignupRequest request, String propertyName) {
		return violationsOn(request, propertyName).findAny().isPresent();
	}

	private String violationMessageOn(SignupRequest request, String propertyName) {
		return violationsOn(request, propertyName).findFirst().orElseThrow().getMessage();
	}

	private Stream<ConstraintViolation<SignupRequest>> violationsOn(SignupRequest request, String propertyName) {
		Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);
		return violations.stream().filter(v -> v.getPropertyPath().toString().equals(propertyName));
	}

}
