package org.calik.clewa.auth.dto;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestTest {

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

	@Test
	void validRequest_hasNoViolations() {
		assertThat(validator.validate(new LoginRequest("+905321234567", "Password1"))).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void blankPhoneNumber_isRejected(String phoneNumber) {
		assertThat(hasViolationOn(new LoginRequest(phoneNumber, "Password1"), "phoneNumber")).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void blankPassword_isRejected(String password) {
		assertThat(hasViolationOn(new LoginRequest("+905321234567", password), "password")).isTrue();
	}

	private boolean hasViolationOn(LoginRequest request, String propertyName) {
		Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
		return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(propertyName));
	}

}
