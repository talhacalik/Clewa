package org.calik.clewa.wallet.dto;

import java.math.BigDecimal;

import org.calik.clewa.wallet.entity.Wallet;

public record WalletResponse(Long walletId, BigDecimal balance) {

	public static WalletResponse from(Wallet wallet) {
		return new WalletResponse(wallet.getId(), wallet.getBalance());
	}

}
