package org.calik.clewa.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.calik.clewa.wallet.entity.Transfer;
import org.calik.clewa.wallet.entity.Wallet;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

	// Kasıtlı olarak sadece idempotencyKey ile değil, gönderen cüzdanla birlikte aranıyor —
	// bkz. Transfer.java'daki uk_transfers_sender_wallet_idempotency_key kısıtının gerekçesi.
	Optional<Transfer> findBySenderWalletAndIdempotencyKey(Wallet senderWallet, String idempotencyKey);

}
