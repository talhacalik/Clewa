package org.calik.clewa.auth;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.auth.dto.SignupRequest;
import org.calik.clewa.auth.exception.PhoneAlreadyRegisteredException;
import org.calik.clewa.auth.exception.UnderageException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SignupService {

	private static final int MINIMUM_AGE = 18;

	private final UserRepository userRepository;
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

		return userRepository.save(user);
	}

}
