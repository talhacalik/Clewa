package org.calik.clewa.card.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardTransaction;
import org.calik.clewa.card.exception.CardConflictException;
import org.calik.clewa.card.exception.CardNotActiveException;
import org.calik.clewa.card.exception.CardNotFoundException;
import org.calik.clewa.card.exception.DailyLimitExceededException;
import org.calik.clewa.card.repository.CardRepository;
import org.calik.clewa.card.repository.CardTransactionRepository;
import org.calik.clewa.common.exception.IdempotencyKeyReuseException;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.InsufficientBalanceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardSpendingServiceTest {

	private static final String PHONE = "+905321234567";
	private static final String KEY = "spend-key-1";
	private static final String MERCHANT = "Migros";

	@Mock
	private CardRepository cardRepository;

	@Mock
	private CardTransactionRepository cardTransactionRepository;

	@InjectMocks
	private CardSpendingService cardSpendingService;

	private Card cardWithBalance(String balance) {
		User user = new User();
		user.setPhoneNumber(PHONE);
		Wallet wallet = new Wallet(user, "1111111111");
		wallet.setBalance(new BigDecimal(balance));
		return new Card(wallet, "1234567890123456");
	}

	private void stubCardAndNoExistingTransaction(Card card) {
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));
		when(cardTransactionRepository.findByCardAndIdempotencyKey(card, KEY)).thenReturn(Optional.empty());
	}

	@Test
	void spend_debitsWalletAndRemainingLimitAndSavesTransaction() {
		Card card = cardWithBalance("1000.00");
		stubCardAndNoExistingTransaction(card);
		when(cardTransactionRepository.save(any(CardTransaction.class))).thenAnswer(i -> i.getArgument(0));

		CardTransaction result = cardSpendingService.spend(PHONE, new BigDecimal("250.00"), MERCHANT, KEY);

		assertThat(card.getWallet().getBalance()).isEqualByComparingTo("750.00");
		assertThat(card.getRemainingDailyLimit()).isEqualByComparingTo("499750.00");
		assertThat(card.getDailyLimit()).isEqualByComparingTo("500000.00");
		assertThat(result.getAmount()).isEqualByComparingTo("250.00");
		assertThat(result.getMerchantName()).isEqualTo(MERCHANT);
		assertThat(result.getIdempotencyKey()).isEqualTo(KEY);
		// Kilit sırası kuralı: önce kart, sonra cüzdan; iki ayrı flush.
		verify(cardRepository, times(2)).flush();
	}

	@Test
	void spend_whenIdempotencyKeyAlreadyUsed_returnsExistingWithoutChargingAgain() {
		Card card = cardWithBalance("1000.00");
		CardTransaction existing = new CardTransaction(card, new BigDecimal("250.00"), MERCHANT, KEY);
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));
		when(cardTransactionRepository.findByCardAndIdempotencyKey(card, KEY)).thenReturn(Optional.of(existing));

		CardTransaction result = cardSpendingService.spend(PHONE, new BigDecimal("250.00"), MERCHANT, KEY);

		assertThat(result).isEqualTo(existing);
		assertThat(card.getWallet().getBalance()).isEqualByComparingTo("1000.00");
		assertThat(card.getRemainingDailyLimit()).isEqualByComparingTo("500000.00");
		verify(cardTransactionRepository, never()).save(any());
	}

	@Test
	void spend_whenSameKeyReusedWithDifferentAmountOrMerchant_throwsIdempotencyKeyReuse() {
		Card card = cardWithBalance("1000.00");
		CardTransaction existing = new CardTransaction(card, new BigDecimal("250.00"), MERCHANT, KEY);
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));
		when(cardTransactionRepository.findByCardAndIdempotencyKey(card, KEY)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("251.00"), MERCHANT, KEY))
			.isInstanceOf(IdempotencyKeyReuseException.class);
		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("250.00"), "BIM", KEY))
			.isInstanceOf(IdempotencyKeyReuseException.class);

		assertThat(card.getWallet().getBalance()).isEqualByComparingTo("1000.00");
	}

	@Test
	void spend_whenUserHasNoCard_throwsCardNotFound() {
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("10.00"), MERCHANT, KEY))
			.isInstanceOf(CardNotFoundException.class);
	}

	@Test
	void spend_whenCardIsFrozen_throwsAndChargesNothing() {
		Card card = cardWithBalance("1000.00");
		card.freeze();
		stubCardAndNoExistingTransaction(card);

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("10.00"), MERCHANT, KEY))
			.isInstanceOf(CardNotActiveException.class);

		assertThat(card.getWallet().getBalance()).isEqualByComparingTo("1000.00");
		verify(cardTransactionRepository, never()).save(any());
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = { "FROZEN", "CLOSED" })
	void spend_whenAccountNotActive_throwsAndChargesNothing(AccountStatus inactiveStatus) {
		Card card = cardWithBalance("1000.00");
		card.getWallet().getUser().setStatus(inactiveStatus);
		stubCardAndNoExistingTransaction(card);

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("10.00"), MERCHANT, KEY))
			.isInstanceOf(AccountNotActiveException.class);

		assertThat(card.getWallet().getBalance()).isEqualByComparingTo("1000.00");
		verify(cardTransactionRepository, never()).save(any());
	}

	@Test
	void spend_whenBalanceInsufficient_throwsAndDoesNotSaveTransaction() {
		Card card = cardWithBalance("100.00");
		stubCardAndNoExistingTransaction(card);

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("250.00"), MERCHANT, KEY))
			.isInstanceOf(InsufficientBalanceException.class);

		verify(cardTransactionRepository, never()).save(any());
	}

	@Test
	void spend_whenAmountExceedsRemainingDailyLimit_throwsEvenIfBalanceIsEnough() {
		Card card = cardWithBalance("999999999.00");
		stubCardAndNoExistingTransaction(card);

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("500000.01"), MERCHANT, KEY))
			.isInstanceOf(DailyLimitExceededException.class);

		assertThat(card.getWallet().getBalance()).isEqualByComparingTo("999999999.00");
		verify(cardTransactionRepository, never()).save(any());
	}

	@Test
	void spend_canUseExactlyTheRemainingLimit() {
		Card card = cardWithBalance("999999999.00");
		stubCardAndNoExistingTransaction(card);
		when(cardTransactionRepository.save(any(CardTransaction.class))).thenAnswer(i -> i.getArgument(0));

		cardSpendingService.spend(PHONE, new BigDecimal("500000.00"), MERCHANT, KEY);

		assertThat(card.getRemainingDailyLimit()).isEqualByComparingTo("0.00");
	}

	@Test
	void spend_whenConcurrentUpdateConflicts_throwsCardConflict() {
		Card card = cardWithBalance("1000.00");
		stubCardAndNoExistingTransaction(card);
		doThrow(new ObjectOptimisticLockingFailureException(Card.class, 1L)).when(cardRepository).flush();

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("10.00"), MERCHANT, KEY))
			.isInstanceOf(CardConflictException.class);

		verify(cardTransactionRepository, never()).save(any());
	}

	@Test
	void spend_whenDuplicateKeyRaceHitsOnSave_throwsCardConflict() {
		Card card = cardWithBalance("1000.00");
		stubCardAndNoExistingTransaction(card);
		when(cardTransactionRepository.save(any(CardTransaction.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("10.00"), MERCHANT, KEY))
			.isInstanceOf(CardConflictException.class);
	}

}
