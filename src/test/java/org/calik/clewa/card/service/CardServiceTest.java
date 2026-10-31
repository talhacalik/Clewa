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
import org.calik.clewa.card.entity.CardStatus;
import org.calik.clewa.card.exception.CardAlreadyExistsException;
import org.calik.clewa.card.exception.CardConflictException;
import org.calik.clewa.card.exception.CardNotFoundException;
import org.calik.clewa.card.repository.CardRepository;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.service.WalletService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

	private static final String PHONE = "+905321234567";

	@Mock
	private CardRepository cardRepository;

	@Mock
	private WalletService walletService;

	@InjectMocks
	private CardService cardService;

	private Wallet buildWallet() {
		User user = new User();
		user.setPhoneNumber(PHONE);
		return new Wallet(user, "1111111111");
	}

	@Test
	void createCard_savesCardForUsersWalletWithGeneratedNumber() {
		Wallet wallet = buildWallet();
		when(walletService.getWalletForCurrentUser(PHONE)).thenReturn(wallet);
		when(cardRepository.existsByWallet(wallet)).thenReturn(false);
		when(cardRepository.existsByCardNumber(anyString())).thenReturn(false);
		when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Card saved = cardService.createCard(PHONE);

		assertThat(saved.getWallet()).isEqualTo(wallet);
		assertThat(saved.getCardNumber()).matches("^[0-9]{16}$");
	}

	@Test
	void createCard_whenUserAlreadyHasCard_throwsAndDoesNotSave() {
		Wallet wallet = buildWallet();
		when(walletService.getWalletForCurrentUser(PHONE)).thenReturn(wallet);
		when(cardRepository.existsByWallet(wallet)).thenReturn(true);

		assertThatThrownBy(() -> cardService.createCard(PHONE)).isInstanceOf(CardAlreadyExistsException.class);

		verify(cardRepository, never()).save(any());
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = { "FROZEN", "CLOSED" })
	void createCard_whenUserAccountNotActive_throwsAndDoesNotSave(AccountStatus inactiveStatus) {
		Wallet wallet = buildWallet();
		wallet.getUser().setStatus(inactiveStatus);
		when(walletService.getWalletForCurrentUser(PHONE)).thenReturn(wallet);

		assertThatThrownBy(() -> cardService.createCard(PHONE)).isInstanceOf(AccountNotActiveException.class);

		verify(cardRepository, never()).save(any());
	}

	@Test
	void createCard_whenCardNumberTaken_retriesUntilUnique() {
		Wallet wallet = buildWallet();
		when(walletService.getWalletForCurrentUser(PHONE)).thenReturn(wallet);
		when(cardRepository.existsByWallet(wallet)).thenReturn(false);
		when(cardRepository.existsByCardNumber(anyString())).thenReturn(true, true, false);
		when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

		cardService.createCard(PHONE);

		verify(cardRepository, times(3)).existsByCardNumber(anyString());
	}

	@Test
	void createCard_whenNoUniqueNumberFound_throwsAndDoesNotSave() {
		Wallet wallet = buildWallet();
		when(walletService.getWalletForCurrentUser(PHONE)).thenReturn(wallet);
		when(cardRepository.existsByWallet(wallet)).thenReturn(false);
		when(cardRepository.existsByCardNumber(anyString())).thenReturn(true);

		assertThatThrownBy(() -> cardService.createCard(PHONE)).isInstanceOf(IllegalStateException.class);

		verify(cardRepository, times(10)).existsByCardNumber(anyString());
		verify(cardRepository, never()).save(any());
	}

	@Test
	void createCard_whenConcurrentRequestWinsTheRace_throwsCardAlreadyExists() {
		Wallet wallet = buildWallet();
		when(walletService.getWalletForCurrentUser(PHONE)).thenReturn(wallet);
		when(cardRepository.existsByWallet(wallet)).thenReturn(false);
		when(cardRepository.existsByCardNumber(anyString())).thenReturn(false);
		when(cardRepository.save(any(Card.class))).thenThrow(new DataIntegrityViolationException("uk_cards_wallet_id"));

		assertThatThrownBy(() -> cardService.createCard(PHONE)).isInstanceOf(CardAlreadyExistsException.class);
	}

	@Test
	void createCard_whenOtherConstraintFails_doesNotReportCardAlreadyExists() {
		Wallet wallet = buildWallet();
		when(walletService.getWalletForCurrentUser(PHONE)).thenReturn(wallet);
		when(cardRepository.existsByWallet(wallet)).thenReturn(false);
		when(cardRepository.existsByCardNumber(anyString())).thenReturn(false);
		when(cardRepository.save(any(Card.class))).thenThrow(new DataIntegrityViolationException("uk_cards_card_number"));

		assertThatThrownBy(() -> cardService.createCard(PHONE)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private Card cardOfUser() {
		return new Card(buildWallet(), "1234567890123456");
	}

	@Test
	void freezeCard_freezesEvenWhenAccountIsNotActive() {
		Card card = cardOfUser();
		card.getWallet().getUser().setStatus(AccountStatus.FROZEN);
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));

		Card result = cardService.freezeCard(PHONE);

		assertThat(result.getStatus()).isEqualTo(CardStatus.FROZEN);
		verify(cardRepository).flush();
	}

	@Test
	void unfreezeCard_activatesCard() {
		Card card = cardOfUser();
		card.freeze();
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));

		Card result = cardService.unfreezeCard(PHONE);

		assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
	}

	@Test
	void unfreezeCard_whenAccountNotActive_throwsAndKeepsCardFrozen() {
		Card card = cardOfUser();
		card.freeze();
		card.getWallet().getUser().setStatus(AccountStatus.FROZEN);
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));

		assertThatThrownBy(() -> cardService.unfreezeCard(PHONE)).isInstanceOf(AccountNotActiveException.class);

		assertThat(card.getStatus()).isEqualTo(CardStatus.FROZEN);
	}

	@Test
	void changeDailyLimit_updatesLimitOnly() {
		Card card = cardOfUser();
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));

		Card result = cardService.changeDailyLimit(PHONE, new BigDecimal("1000.00"));

		assertThat(result.getDailyLimit()).isEqualByComparingTo("1000.00");
		assertThat(result.getRemainingDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@Test
	void changeDailyLimit_whenAccountNotActive_throws() {
		Card card = cardOfUser();
		card.getWallet().getUser().setStatus(AccountStatus.CLOSED);
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));

		assertThatThrownBy(() -> cardService.changeDailyLimit(PHONE, new BigDecimal("1000.00")))
			.isInstanceOf(AccountNotActiveException.class);

		assertThat(card.getDailyLimit()).isEqualByComparingTo("500000.00");
	}

	@Test
	void freezeCard_whenUserHasNoCard_throwsCardNotFound() {
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> cardService.freezeCard(PHONE)).isInstanceOf(CardNotFoundException.class);
	}

	@Test
	void freezeCard_whenConcurrentUpdateConflicts_throwsCardConflict() {
		Card card = cardOfUser();
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));
		doThrow(new ObjectOptimisticLockingFailureException(Card.class, 1L)).when(cardRepository).flush();

		assertThatThrownBy(() -> cardService.freezeCard(PHONE)).isInstanceOf(CardConflictException.class);
	}

	@Test
	void unfreezeCard_whenUserHasNoCard_throwsCardNotFound() {
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> cardService.unfreezeCard(PHONE)).isInstanceOf(CardNotFoundException.class);
	}

	@Test
	void changeDailyLimit_whenUserHasNoCard_throwsCardNotFound() {
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> cardService.changeDailyLimit(PHONE, new BigDecimal("1000.00")))
			.isInstanceOf(CardNotFoundException.class);
	}

	@Test
	void unfreezeCard_whenConcurrentUpdateConflicts_throwsCardConflict() {
		Card card = cardOfUser();
		card.freeze();
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));
		doThrow(new ObjectOptimisticLockingFailureException(Card.class, 1L)).when(cardRepository).flush();

		assertThatThrownBy(() -> cardService.unfreezeCard(PHONE)).isInstanceOf(CardConflictException.class);
	}

	@Test
	void changeDailyLimit_whenConcurrentUpdateConflicts_throwsCardConflict() {
		Card card = cardOfUser();
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));
		doThrow(new ObjectOptimisticLockingFailureException(Card.class, 1L)).when(cardRepository).flush();

		assertThatThrownBy(() -> cardService.changeDailyLimit(PHONE, new BigDecimal("1000.00")))
			.isInstanceOf(CardConflictException.class);
	}

	@Test
	void getCard_returnsCardOfAuthenticatedUser() {
		Card card = new Card(buildWallet(), "1234567890123456");
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.of(card));

		assertThat(cardService.getCardForCurrentUser(PHONE)).isEqualTo(card);
	}

	@Test
	void getCard_whenUserHasNoCard_throwsCardNotFound() {
		when(cardRepository.findByWallet_User_PhoneNumber(PHONE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> cardService.getCardForCurrentUser(PHONE)).isInstanceOf(CardNotFoundException.class);
	}

}
