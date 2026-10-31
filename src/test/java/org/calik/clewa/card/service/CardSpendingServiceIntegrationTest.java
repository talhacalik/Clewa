package org.calik.clewa.card.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import org.calik.clewa.AbstractIntegrationTest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardTransaction;
import org.calik.clewa.card.exception.DailyLimitExceededException;
import org.calik.clewa.card.repository.CardRepository;
import org.calik.clewa.card.repository.CardTransactionRepository;
import org.calik.clewa.common.config.JpaAuditingConfig;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.InsufficientBalanceException;
import org.calik.clewa.wallet.repository.WalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Mock'suz, gerçek Postgres'e karşı: bakiye ve limitin gerçekten kalıcı olarak düştüğünü ve
// tekrar eden isteğin ikinci kez ücretlendirmediğini kanıtlar.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ JpaAuditingConfig.class, CardSpendingService.class })
class CardSpendingServiceIntegrationTest extends AbstractIntegrationTest {

	private static final String PHONE = "+905321234567";

	@Autowired
	private CardSpendingService cardSpendingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private CardRepository cardRepository;

	@Autowired
	private CardTransactionRepository cardTransactionRepository;

	@Autowired
	private EntityManager entityManager;

	private Card persistedCard(String balance) {
		User user = new User();
		user.setPhoneNumber(PHONE);
		user.setPasswordHash("hashed-password");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		user.setDateOfBirth(LocalDate.of(1995, 3, 20));
		userRepository.save(user);
		Wallet wallet = new Wallet(user, "1111111111");
		wallet.setBalance(new BigDecimal(balance));
		walletRepository.save(wallet);
		Card card = cardRepository.saveAndFlush(new Card(wallet, "1234567890123456"));
		entityManager.clear();
		return card;
	}

	private BigDecimal walletBalance() {
		entityManager.clear();
		return walletRepository.findByUser_PhoneNumber(PHONE).orElseThrow().getBalance();
	}

	private BigDecimal remainingLimit() {
		entityManager.clear();
		return cardRepository.findByWallet_User_PhoneNumber(PHONE).orElseThrow().getRemainingDailyLimit();
	}

	@Test
	void spend_persistsBalanceLimitAndTransaction() {
		persistedCard("1000.00");

		CardTransaction transaction = cardSpendingService.spend(PHONE, new BigDecimal("250.00"), "Migros", "key-1");
		entityManager.flush();

		assertThat(transaction.getId()).isNotNull();
		assertThat(walletBalance()).isEqualByComparingTo("750.00");
		assertThat(remainingLimit()).isEqualByComparingTo("499750.00");
		assertThat(cardTransactionRepository.count()).isEqualTo(1);
	}

	@Test
	void spend_repeatedWithSameKey_chargesOnlyOnce() {
		persistedCard("1000.00");

		CardTransaction first = cardSpendingService.spend(PHONE, new BigDecimal("250.00"), "Migros", "same-key");
		entityManager.flush();
		entityManager.clear();
		CardTransaction second = cardSpendingService.spend(PHONE, new BigDecimal("250.00"), "Migros", "same-key");

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(walletBalance()).isEqualByComparingTo("750.00");
		assertThat(remainingLimit()).isEqualByComparingTo("499750.00");
		assertThat(cardTransactionRepository.count()).isEqualTo(1);
	}

	@Test
	void spend_withInsufficientBalance_changesNothing() {
		persistedCard("100.00");

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("250.00"), "Migros", "key-2"))
			.isInstanceOf(InsufficientBalanceException.class);
		entityManager.clear();

		assertThat(cardTransactionRepository.count()).isZero();
	}

	@Test
	void spend_overDailyLimit_isRejectedAndLimitStaysIntact() {
		persistedCard("999999999.00");
		cardSpendingService.spend(PHONE, new BigDecimal("400000.00"), "Migros", "key-3");
		entityManager.flush();

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("100000.01"), "Migros", "key-4"))
			.isInstanceOf(DailyLimitExceededException.class);

		assertThat(remainingLimit()).isEqualByComparingTo("100000.00");
	}

}
