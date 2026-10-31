package org.calik.clewa.card.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.exception.CardAlreadyExistsException;
import org.calik.clewa.card.exception.CardNotFoundException;
import org.calik.clewa.card.repository.CardRepository;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.service.WalletService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
