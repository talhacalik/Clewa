package org.calik.clewa.card.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import org.calik.clewa.AbstractIntegrationTest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardStatus;
import org.calik.clewa.common.config.JpaAuditingConfig;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.repository.WalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class CardRepositoryTest extends AbstractIntegrationTest {

	private static final String CARD_NUMBER = "1234567890123456";

	@Autowired
	private CardRepository cardRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Wallet persistedWallet(String phoneNumber, String accountNumber) {
		User user = new User();
		user.setPhoneNumber(phoneNumber);
		user.setPasswordHash("hashed-password");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		user.setDateOfBirth(LocalDate.of(1995, 3, 20));
		userRepository.save(user);
		return walletRepository.save(new Wallet(user, accountNumber));
	}

	@Test
	void save_shouldPersistCardWithDefaults() {
		Wallet wallet = persistedWallet("+905321234567", "1111111111");

		Card saved = cardRepository.saveAndFlush(new Card(wallet, CARD_NUMBER));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getWallet().getId()).isEqualTo(wallet.getId());
		assertThat(saved.getCardNumber()).isEqualTo(CARD_NUMBER);
		assertThat(saved.getStatus()).isEqualTo(CardStatus.ACTIVE);
		assertThat(saved.getDailyLimit()).isEqualByComparingTo("500000.00");
		assertThat(saved.getDailyLimit().scale()).isEqualTo(2);
		assertThat(saved.getRemainingDailyLimit()).isEqualByComparingTo("500000.00");
		assertThat(saved.getRemainingDailyLimit().scale()).isEqualTo(2);
		assertThat(saved.getVersion()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	void save_whenSecondCardForSameWallet_violatesUniqueConstraint() {
		Wallet wallet = persistedWallet("+905321234567", "1111111111");
		cardRepository.saveAndFlush(new Card(wallet, CARD_NUMBER));

		assertThatThrownBy(() -> cardRepository.saveAndFlush(new Card(wallet, "6543210987654321")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_cards_wallet_id");
	}

	@Test
	void save_whenCardNumberAlreadyExists_violatesUniqueConstraint() {
		Wallet firstWallet = persistedWallet("+905321234567", "1111111111");
		Wallet secondWallet = persistedWallet("+905339876543", "2222222222");
		cardRepository.saveAndFlush(new Card(firstWallet, CARD_NUMBER));

		assertThatThrownBy(() -> cardRepository.saveAndFlush(new Card(secondWallet, CARD_NUMBER)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_cards_card_number");
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "123456789012345", "123456789012345a", "abcdefghijklmnop" })
	void save_whenCardNumberFormatInvalid_violatesCheckConstraint(String invalidCardNumber) {
		Wallet wallet = persistedWallet("+905321234567", "1111111111");

		assertThatThrownBy(() -> cardRepository.saveAndFlush(new Card(wallet, invalidCardNumber)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_cards_card_number_format");
	}

	@Test
	void save_whenWalletIsNull_violatesNotNullConstraint() {
		assertThatThrownBy(() -> cardRepository.saveAndFlush(new Card(null, CARD_NUMBER)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_whenCardNumberIsNull_violatesNotNullConstraint() {
		Wallet wallet = persistedWallet("+905321234567", "1111111111");

		assertThatThrownBy(() -> cardRepository.saveAndFlush(new Card(wallet, null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_whenRemainingDailyLimitIsNegative_violatesCheckConstraint() {
		Wallet wallet = persistedWallet("+905321234567", "1111111111");
		Card card = new Card(wallet, CARD_NUMBER);
		ReflectionTestUtils.setField(card, "remainingDailyLimit", new BigDecimal("-0.01"));

		assertThatThrownBy(() -> cardRepository.saveAndFlush(card))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_cards_remaining_daily_limit_non_negative");
	}

	@Test
	void save_whenRemainingDailyLimitIsZero_isAllowed() {
		Wallet wallet = persistedWallet("+905321234567", "1111111111");
		Card card = new Card(wallet, CARD_NUMBER);
		ReflectionTestUtils.setField(card, "remainingDailyLimit", new BigDecimal("0.00"));

		Card saved = cardRepository.saveAndFlush(card);

		assertThat(saved.getRemainingDailyLimit()).isEqualByComparingTo("0.00");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.00", "-1.00" })
	void save_whenDailyLimitIsNotPositive_violatesCheckConstraint(String invalidLimit) {
		Wallet wallet = persistedWallet("+905321234567", "1111111111");
		Card card = new Card(wallet, CARD_NUMBER);
		ReflectionTestUtils.setField(card, "dailyLimit", new BigDecimal(invalidLimit));

		assertThatThrownBy(() -> cardRepository.saveAndFlush(card))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_cards_daily_limit_positive");
	}

	@Test
	void databaseRejectsUnknownStatusValue() {
		Wallet wallet = persistedWallet("+905321234567", "1111111111");
		Card saved = cardRepository.saveAndFlush(new Card(wallet, CARD_NUMBER));

		assertThatThrownBy(() -> jdbcTemplate.update("update cards set status = 'XYZ' where id = ?", saved.getId()))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("violates check constraint");
	}

}
