package org.calik.clewa.wallet.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.WalletNotFoundException;
import org.calik.clewa.wallet.repository.WalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

	@Mock
	private WalletRepository walletRepository;

	@InjectMocks
	private WalletService walletService;

	@Test
	void getWalletForCurrentUser_returnsWalletForAuthenticatedPhoneNumber() {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		Wallet wallet = new Wallet(user, "1234567890");
		wallet.setBalance(new BigDecimal("0.00"));

		when(walletRepository.findByUser_PhoneNumber("+905321234567")).thenReturn(Optional.of(wallet));

		Wallet result = walletService.getWalletForCurrentUser("+905321234567");

		assertThat(result).isEqualTo(wallet);
	}

	@Test
	void getWalletForCurrentUser_whenWalletNotFound_throws() {
		when(walletRepository.findByUser_PhoneNumber("+905321234567")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> walletService.getWalletForCurrentUser("+905321234567"))
			.isInstanceOf(WalletNotFoundException.class);
	}

	@Test
	void createWallet_generatesTenDigitAccountNumberAndSavesWallet() {
		User user = new User();
		when(walletRepository.existsByAccountNumber(anyString())).thenReturn(false);
		when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Wallet saved = walletService.createWallet(user);

		assertThat(saved.getUser()).isEqualTo(user);
		assertThat(saved.getAccountNumber()).matches("^[0-9]{10}$");
	}

	@Test
	void createWallet_whenAccountNumberTaken_retriesUntilUnique() {
		User user = new User();
		when(walletRepository.existsByAccountNumber(anyString())).thenReturn(true, true, false);
		when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

		walletService.createWallet(user);

		verify(walletRepository, times(3)).existsByAccountNumber(anyString());
	}

	@Test
	void createWallet_whenAccountNumberFoundOnLastAttempt_succeeds() {
		User user = new User();
		// MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS = 10: ilk 9 deneme dolu, tam 10. denemede müsait.
		when(walletRepository.existsByAccountNumber(anyString()))
			.thenReturn(true, true, true, true, true, true, true, true, true, false);
		when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Wallet saved = walletService.createWallet(user);

		assertThat(saved.getAccountNumber()).matches("^[0-9]{10}$");
		verify(walletRepository, times(10)).existsByAccountNumber(anyString());
	}

	@Test
	void createWallet_whenNoUniqueAccountNumberFound_throwsAndDoesNotSave() {
		User user = new User();
		when(walletRepository.existsByAccountNumber(anyString())).thenReturn(true);

		assertThatThrownBy(() -> walletService.createWallet(user))
			.isInstanceOf(IllegalStateException.class);

		verify(walletRepository, times(10)).existsByAccountNumber(anyString());
		verify(walletRepository, never()).save(any());
	}

}
