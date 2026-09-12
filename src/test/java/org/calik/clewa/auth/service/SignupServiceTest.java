package org.calik.clewa.auth.service;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.calik.clewa.auth.dto.SignupRequest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.InvalidPhoneNumberException;
import org.calik.clewa.auth.exception.PhoneAlreadyRegisteredException;
import org.calik.clewa.auth.exception.UnderageException;
import org.calik.clewa.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private SignupService signupService;

	private SignupRequest validRequest() {
		return new SignupRequest("5321234567", "Password1", "Ahmet", "Yılmaz", LocalDate.now().minusYears(20));
	}

	@Test
	void signup_withValidRequest_savesUserWithHashedPasswordAndNormalizedPhone() {
		SignupRequest request = validRequest();
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("Password1")).thenReturn("hashed-password");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User saved = signupService.signup(request);

		assertThat(saved.getPhoneNumber()).isEqualTo("+905321234567");
		assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
		assertThat(saved.getFirstName()).isEqualTo("Ahmet");
		assertThat(saved.getLastName()).isEqualTo("Yılmaz");
		assertThat(saved.isPhoneVerified()).isFalse();
		assertThat(saved.getVerificationCode()).matches("\\d{6}");
		assertThat(saved.getVerificationCodeExpiresAt()).isAfter(java.time.Instant.now());
	}

	@Test
	void signup_whenUnderage_throwsAndDoesNotSave() {
		SignupRequest request = new SignupRequest("5321234567", "Password1", "Ahmet", "Yılmaz", LocalDate.now().minusYears(17));

		assertThatThrownBy(() -> signupService.signup(request))
			.isInstanceOf(UnderageException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void signup_whenPhoneAlreadyRegistered_throwsAndDoesNotSave() {
		SignupRequest request = validRequest();
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(new User()));

		assertThatThrownBy(() -> signupService.signup(request))
			.isInstanceOf(PhoneAlreadyRegisteredException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void signup_whenExactly18YearsOld_isAccepted() {
		SignupRequest request = new SignupRequest("5321234567", "Password1", "Ahmet", "Yılmaz", LocalDate.now().minusYears(18));
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("Password1")).thenReturn("hashed-password");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatCode(() -> signupService.signup(request)).doesNotThrowAnyException();
	}

	@Test
	void signup_whenPhoneNumberInvalid_throwsAndDoesNotSave() {
		SignupRequest request = new SignupRequest("123", "Password1", "Ahmet", "Yılmaz", LocalDate.now().minusYears(20));

		assertThatThrownBy(() -> signupService.signup(request))
			.isInstanceOf(InvalidPhoneNumberException.class);

		verify(userRepository, never()).save(any());
	}

}
