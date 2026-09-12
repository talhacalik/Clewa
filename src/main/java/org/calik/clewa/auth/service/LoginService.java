package org.calik.clewa.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.calik.clewa.auth.dto.LoginRequest;
import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.auth.exception.InvalidCredentialsException;
import org.calik.clewa.auth.exception.PhoneNotVerifiedException;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.auth.util.PhoneNumberNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoginService {

	// Telefon numarası kayıtlı değilse şifre karşılaştırması hiç çalışmaz; bu da "numara var mı yok mu"
	// bilgisinin yanıt SÜRESİNDEN sızmasına yol açar (BCrypt kontrolü kasıtlı olarak yavaştır).
	// Bunu önlemek için, numara bulunamasa bile sahte bir hash'e karşı karşılaştırma çalıştırılır.
	private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public User login(LoginRequest request) {
		String normalizedPhoneNumber = PhoneNumberNormalizer.normalize(request.phoneNumber());
		User user = userRepository.findByPhoneNumber(normalizedPhoneNumber).orElse(null);

		String hashToCheck = user != null ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;
		boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

		if (user == null || !passwordMatches) {
			throw new InvalidCredentialsException("Telefon numarası veya şifre hatalı.");
		}

		if (user.getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException("Hesabınız aktif değil.");
		}

		if (!user.isPhoneVerified()) {
			throw new PhoneNotVerifiedException("Giriş yapmadan önce telefon numaranızı doğrulamalısınız.");
		}

		return user;
	}

}
