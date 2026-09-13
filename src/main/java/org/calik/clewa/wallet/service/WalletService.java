package org.calik.clewa.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.WalletNotFoundException;
import org.calik.clewa.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletService {

	private final WalletRepository walletRepository;

	@Transactional(readOnly = true)
	public Wallet getWalletForCurrentUser(String phoneNumber) {
		return walletRepository.findByUser_PhoneNumber(phoneNumber)
			.orElseThrow(() -> new WalletNotFoundException("Oturumdaki kullanıcının cüzdanı bulunamadı."));
	}

}
