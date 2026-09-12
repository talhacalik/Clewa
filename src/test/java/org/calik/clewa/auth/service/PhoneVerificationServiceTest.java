package org.calik.clewa.auth.service;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.calik.clewa.auth.dto.VerifyPhoneRequest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.InvalidVerificationCodeException;
import org.calik.clewa.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private PhoneVerificationService phoneVerificationService;

	private User userWithCode(String code, Instant expiresAt) {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		user.setVerificationCode(code);
		user.setVerificationCodeExpiresAt(expiresAt);
		return user;
	}

	@Test
	void verify_withCorrectAndUnexpiredCode_marksPhoneAsVerified() {
		User user = userWithCode("123456", Instant.now().plusSeconds(60));
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		phoneVerificationService.verify(new VerifyPhoneRequest("5321234567", "123456"));

		assertThat(user.isPhoneVerified()).isTrue();
	}

	@Test
	void verify_withIncorrectCode_throwsAndDoesNotVerify() {
		User user = userWithCode("123456", Instant.now().plusSeconds(60));
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> phoneVerificationService.verify(new VerifyPhoneRequest("5321234567", "000000")))
			.isInstanceOf(InvalidVerificationCodeException.class);

		assertThat(user.isPhoneVerified()).isFalse();
	}

	@Test
	void verify_withExpiredCode_throwsAndDoesNotVerify() {
		User user = userWithCode("123456", Instant.now().minusSeconds(1));
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> phoneVerificationService.verify(new VerifyPhoneRequest("5321234567", "123456")))
			.isInstanceOf(InvalidVerificationCodeException.class);

		assertThat(user.isPhoneVerified()).isFalse();
	}

	@Test
	void verify_withUnknownPhoneNumber_throwsInvalidVerificationCodeException() {
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> phoneVerificationService.verify(new VerifyPhoneRequest("5321234567", "123456")))
			.isInstanceOf(InvalidVerificationCodeException.class);
	}

	@Test
	void verify_withIncorrectCode_incrementsAttemptCount() {
		User user = userWithCode("123456", Instant.now().plusSeconds(60));
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> phoneVerificationService.verify(new VerifyPhoneRequest("5321234567", "000000")))
			.isInstanceOf(InvalidVerificationCodeException.class);

		assertThat(user.getVerificationAttempts()).isEqualTo(1);
	}

	@Test
	void verify_afterMaxFailedAttempts_invalidatesCodeEvenIfCorrectCodeTriedAfterward() {
		User user = userWithCode("123456", Instant.now().plusSeconds(60));
		when(userRepository.findByPhoneNumber("+905321234567")).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> phoneVerificationService.verify(new VerifyPhoneRequest("5321234567", "000000")))
				.isInstanceOf(InvalidVerificationCodeException.class);
		}

		assertThat(user.getVerificationCode()).isNull();

		assertThatThrownBy(() -> phoneVerificationService.verify(new VerifyPhoneRequest("5321234567", "123456")))
			.isInstanceOf(InvalidVerificationCodeException.class);
	}

}
