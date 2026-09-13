package org.calik.clewa.wallet.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import org.calik.clewa.AbstractIntegrationTest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.common.config.JpaAuditingConfig;
import org.calik.clewa.wallet.entity.Wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class WalletRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private UserRepository userRepository;

	private User persistedUser() {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		user.setPasswordHash("hashed-password");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		user.setDateOfBirth(LocalDate.of(1995, 3, 20));
		return userRepository.save(user);
	}

	@Test
	void findByUserId_shouldReturnSavedWalletWithDefaults() {
		User user = persistedUser();
		Wallet wallet = new Wallet(user);

		walletRepository.save(wallet);

		var found = walletRepository.findByUserId(user.getId());

		assertThat(found).isPresent();
		assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
		assertThat(found.get().getVersion()).isNotNull();
		assertThat(found.get().getCreatedAt()).isNotNull();
		assertThat(found.get().getUpdatedAt()).isNotNull();
	}

	@Test
	void findByUserId_whenNoWalletExists_returnsEmpty() {
		var found = walletRepository.findByUserId(999L);

		assertThat(found).isEmpty();
	}

	@Test
	void findByUserPhoneNumber_shouldReturnSavedWallet() {
		User user = persistedUser();
		walletRepository.save(new Wallet(user));

		var found = walletRepository.findByUser_PhoneNumber("+905321234567");

		assertThat(found).isPresent();
		assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
	}

	@Test
	void save_whenSecondWalletForSameUser_violatesUniqueConstraint() {
		User user = persistedUser();
		walletRepository.saveAndFlush(new Wallet(user));

		assertThatThrownBy(() -> walletRepository.saveAndFlush(new Wallet(user)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_whenBalanceIsNegative_violatesCheckConstraint() {
		User user = persistedUser();
		Wallet wallet = new Wallet(user);
		wallet.setBalance(new BigDecimal("-10.00"));

		assertThatThrownBy(() -> walletRepository.saveAndFlush(wallet))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
