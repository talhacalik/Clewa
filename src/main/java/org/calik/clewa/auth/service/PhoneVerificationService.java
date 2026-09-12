package org.calik.clewa.auth.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.auth.dto.VerifyPhoneRequest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.InvalidVerificationCodeException;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.auth.util.PhoneNumberNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

	private static final int MAX_VERIFICATION_ATTEMPTS = 5;
	private static final String INVALID_CODE_MESSAGE = "Doğrulama kodu geçersiz veya süresi dolmuş.";

	private final UserRepository userRepository;

	@Transactional
	public void verify(VerifyPhoneRequest request) {
		String normalizedPhoneNumber = PhoneNumberNormalizer.normalize(request.phoneNumber());
		User user = userRepository.findByPhoneNumber(normalizedPhoneNumber).orElse(null);

		boolean codeIsValid = user != null
			&& request.code().equals(user.getVerificationCode())
			&& user.getVerificationCodeExpiresAt() != null
			&& user.getVerificationCodeExpiresAt().isAfter(Instant.now());

		if (!codeIsValid) {
			if (user != null) {
				registerFailedAttempt(user);
			}
			throw new InvalidVerificationCodeException(INVALID_CODE_MESSAGE);
		}

		user.setPhoneVerified(true);
		user.setVerificationCode(null);
		user.setVerificationCodeExpiresAt(null);
		user.setVerificationAttempts(0);
		userRepository.save(user);
	}

	private void registerFailedAttempt(User user) {
		user.setVerificationAttempts(user.getVerificationAttempts() + 1);
		if (user.getVerificationAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
			user.setVerificationCode(null);
			user.setVerificationCodeExpiresAt(null);
		}
		userRepository.save(user);
	}

}
