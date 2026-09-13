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
		Wallet wallet = new Wallet(user);
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

}
