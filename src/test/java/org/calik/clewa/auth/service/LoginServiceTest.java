package org.calik.clewa.auth.service;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.calik.clewa.auth.dto.LoginRequest;
import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.auth.exception.InvalidCredentialsException;
import org.calik.clewa.auth.exception.PhoneNotVerifiedException;
import org.calik.clewa.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private LoginService loginService;

	private User activeVerifiedUser() {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		user.setPasswordHash("hashed-password");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		user.setDateOfBirth(LocalDate.of(1995, 3, 20));
		user.setStatus(AccountStatus.ACTIVE);
		user.setPhoneVerified(true);
		return user;
	}

	@Test
	void login_withValidCredentials_returnsUser() {
		User user = activeVerifiedUser();
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Password1", "hashed-password")).thenReturn(true);

		User result = loginService.login(new LoginRequest("5321234567", "Password1"));

		assertThat(result).isSameAs(user);
	}

	@Test
	void login_withUnknownPhoneNumber_throwsInvalidCredentialsException() {
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loginService.login(new LoginRequest("5321234567", "Password1")))
			.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void login_withUnknownPhoneNumber_stillPerformsPasswordComparison() {
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loginService.login(new LoginRequest("5321234567", "Password1")))
			.isInstanceOf(InvalidCredentialsException.class);

		verify(passwordEncoder).matches(eq("Password1"), any());
	}

	@Test
	void login_withWrongPassword_throwsInvalidCredentialsException() {
		User user = activeVerifiedUser();
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("WrongPassword1", "hashed-password")).thenReturn(false);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("5321234567", "WrongPassword1")))
			.isInstanceOf(InvalidCredentialsException.class);
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = { "FROZEN", "CLOSED" })
	void login_withInactiveAccount_throwsAccountNotActiveException(AccountStatus status) {
		User user = activeVerifiedUser();
		user.setStatus(status);
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Password1", "hashed-password")).thenReturn(true);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("5321234567", "Password1")))
			.isInstanceOf(AccountNotActiveException.class);
	}

	@Test
	void login_withUnverifiedPhone_throwsPhoneNotVerifiedException() {
		User user = activeVerifiedUser();
		user.setPhoneVerified(false);
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Password1", "hashed-password")).thenReturn(true);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("5321234567", "Password1")))
			.isInstanceOf(PhoneNotVerifiedException.class);
	}

}
