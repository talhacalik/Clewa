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

import org.calik.clewa.AbstractIntegrationTest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardTransaction;
import org.calik.clewa.common.config.JpaAuditingConfig;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.repository.WalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class CardTransactionRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private CardTransactionRepository cardTransactionRepository;

	@Autowired
	private CardRepository cardRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private UserRepository userRepository;

	private Card persistedCard(String phoneNumber, String accountNumber, String cardNumber) {
		User user = new User();
		user.setPhoneNumber(phoneNumber);
		user.setPasswordHash("hashed-password");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		user.setDateOfBirth(LocalDate.of(1995, 3, 20));
		userRepository.save(user);
		Wallet wallet = walletRepository.save(new Wallet(user, accountNumber));
		return cardRepository.save(new Card(wallet, cardNumber));
	}

	private Card defaultCard() {
		return persistedCard("+905321234567", "1111111111", "1234567890123456");
	}

	@Test
	void save_shouldPersistTransactionWithAuditFieldAndNormalizedAmount() {
		Card card = defaultCard();

		CardTransaction saved = cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("25.5"), "Migros", "key-1"));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCard().getId()).isEqualTo(card.getId());
		assertThat(saved.getAmount()).isEqualByComparingTo("25.50");
		assertThat(saved.getAmount().scale()).isEqualTo(2);
		assertThat(saved.getMerchantName()).isEqualTo("Migros");
		assertThat(saved.getIdempotencyKey()).isEqualTo("key-1");
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	@Test
	void findByCardAndIdempotencyKey_findsOnlyTheOwnersTransaction() {
		Card firstCard = defaultCard();
		Card secondCard = persistedCard("+905339876543", "2222222222", "6543210987654321");
		cardTransactionRepository.save(new CardTransaction(firstCard, new BigDecimal("10.00"), "Migros", "shared-key"));

		assertThat(cardTransactionRepository.findByCardAndIdempotencyKey(firstCard, "shared-key")).isPresent();
		assertThat(cardTransactionRepository.findByCardAndIdempotencyKey(secondCard, "shared-key")).isEmpty();
		assertThat(cardTransactionRepository.findByCardAndIdempotencyKey(firstCard, "other")).isEmpty();
	}

	@Test
	void save_whenSameIdempotencyKeyForSameCard_violatesUniqueConstraint() {
		Card card = defaultCard();
		cardTransactionRepository.saveAndFlush(new CardTransaction(card, new BigDecimal("10.00"), "Migros", "dup"));

		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("20.00"), "Migros", "dup")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_card_transactions_card_idempotency_key");
	}

	@Test
	void save_whenSameIdempotencyKeyForDifferentCards_isAllowed() {
		Card firstCard = defaultCard();
		Card secondCard = persistedCard("+905339876543", "2222222222", "6543210987654321");
		cardTransactionRepository.saveAndFlush(new CardTransaction(firstCard, new BigDecimal("10.00"), "Migros", "k"));

		CardTransaction second = cardTransactionRepository
			.saveAndFlush(new CardTransaction(secondCard, new BigDecimal("10.00"), "Migros", "k"));

		assertThat(second.getId()).isNotNull();
	}

	@Test
	void save_whenAmountIsNotPositive_violatesCheckConstraint() {
		Card card = defaultCard();

		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("0.00"), "Migros", "key-2")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_card_transactions_amount_positive");
	}

	@Test
	void save_whenAmountIsNegative_violatesCheckConstraint() {
		Card card = defaultCard();

		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("-1.00"), "Migros", "key-neg")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_card_transactions_amount_positive");
	}

	@Test
	void constructor_whenAmountIsNull_throwsNullPointerException() {
		Card card = defaultCard();

		assertThatThrownBy(() -> new CardTransaction(card, null, "Migros", "key-null"))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void save_whenCardIsNull_violatesNotNullConstraint() {
		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(null, new BigDecimal("10.00"), "Migros", "key-3")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("card_id");
	}

	@Test
	void save_whenMerchantNameIsNull_violatesNotNullConstraint() {
		Card card = defaultCard();

		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("10.00"), null, "key-4")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("merchant_name");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void save_whenMerchantNameIsBlank_violatesCheckConstraint(String blankName) {
		Card card = defaultCard();

		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("10.00"), blankName, "key-blank")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("chk_card_transactions_merchant_name_not_blank");
	}

	@Test
	void save_whenMerchantNameIsTooLong_isRejected() {
		Card card = defaultCard();
		String tooLong = "m".repeat(CardTransaction.MERCHANT_NAME_MAX_LENGTH + 1);

		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("10.00"), tooLong, "key-5")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("merchant_name");
	}

	@Test
	void save_whenIdempotencyKeyIsNull_violatesNotNullConstraint() {
		Card card = defaultCard();

		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("10.00"), "Migros", null)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("idempotency_key");
	}

	@Test
	void save_whenIdempotencyKeyIsTooLong_isRejected() {
		Card card = defaultCard();
		String tooLong = "k".repeat(CardTransaction.IDEMPOTENCY_KEY_MAX_LENGTH + 1);

		assertThatThrownBy(() -> cardTransactionRepository
			.saveAndFlush(new CardTransaction(card, new BigDecimal("10.00"), "Migros", tooLong)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("idempotency_key");
	}

}
