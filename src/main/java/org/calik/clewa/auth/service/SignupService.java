package org.calik.clewa.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.auth.dto.SignupRequest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.PhoneAlreadyRegisteredException;
import org.calik.clewa.auth.exception.UnderageException;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.auth.util.PhoneNumberNormalizer;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SignupService {

	private static final Logger logger = LoggerFactory.getLogger(SignupService.class);

	private static final int MINIMUM_AGE = 18;
	private static final long VERIFICATION_CODE_VALIDITY_MINUTES = 5;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final WalletRepository walletRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public User signup(SignupRequest request) {
		if (Period.between(request.dateOfBirth(), LocalDate.now()).getYears() < MINIMUM_AGE) {
			throw new UnderageException("Kayıt olmak için en az 18 yaşında olmalısınız.");
		}

		String normalizedPhoneNumber = PhoneNumberNormalizer.normalize(request.phoneNumber());

		if (userRepository.findByPhoneNumber(normalizedPhoneNumber).isPresent()) {
			throw new PhoneAlreadyRegisteredException("Bu telefon numarası zaten kayıtlı.");
		}

		User user = new User();
		user.setPhoneNumber(normalizedPhoneNumber);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());
		user.setDateOfBirth(request.dateOfBirth());
		String verificationCode = generateVerificationCode();
		user.setVerificationCode(verificationCode);
		user.setVerificationCodeExpiresAt(Instant.now().plus(VERIFICATION_CODE_VALIDITY_MINUTES, ChronoUnit.MINUTES));

		logger.info("[Simüle SMS] {} numarasına doğrulama kodu: {}", normalizedPhoneNumber, verificationCode);

		User savedUser = userRepository.save(user);
		walletRepository.save(new Wallet(savedUser));

		return savedUser;
	}

	private String generateVerificationCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

}
