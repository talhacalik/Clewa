package org.calik.clewa.wallet.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.wallet.entity.Transfer;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.RecipientNotFoundException;
import org.calik.clewa.wallet.exception.SelfTransferException;
import org.calik.clewa.wallet.exception.TransferConflictException;
import org.calik.clewa.wallet.repository.TransferRepository;
import org.calik.clewa.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferService {

	private final WalletRepository walletRepository;
	private final TransferRepository transferRepository;
	private final WalletService walletService;

	// senderPhoneNumber SADECE oturumdaki (Authentication) kullanıcıdan gelmeli, asla client'ın
	// request body'sinden değil — bu metot bunu derleyici seviyesinde zorlamıyor, çağıran (Faz 5'teki
	// controller) bu invaryantı korumaktan sorumlu (security-reviewer, Faz 4 incelemesi).
	@Transactional
	public Transfer transfer(String senderPhoneNumber, String recipientAccountNumber, BigDecimal amount,
			String idempotencyKey) {

		Wallet senderWallet = walletService.getWalletForCurrentUser(senderPhoneNumber);

		// idempotencyKey kasıtlı olarak senderWallet ile birlikte aranıyor, tek başına değil: aksi
		// halde iki farklı kullanıcı aynı key değerini kullanırsa, biri diğerinin transfer kaydını
		// hiçbir yetki kontrolü olmadan görebilirdi (bkz. Transfer.java'daki constraint gerekçesi).
		Optional<Transfer> existingTransfer =
				transferRepository.findBySenderWalletAndIdempotencyKey(senderWallet, idempotencyKey);
		if (existingTransfer.isPresent()) {
			return existingTransfer.get();
		}

		// Alıcı cüzdanı DB'den çekmeden, doğrudan hesap numaralarını karşılaştırıyoruz: kendine transfer
		// zaten reddedilecekse gereksiz bir sorgu yapmaya gerek yok.
		if (recipientAccountNumber.equals(senderWallet.getAccountNumber())) {
			throw new SelfTransferException("Kendi hesabınıza transfer yapamazsınız.");
		}

		Wallet receiverWallet = walletRepository.findByAccountNumber(recipientAccountNumber)
				.orElseThrow(() -> new RecipientNotFoundException("Alıcı bulunamadı."));

		validateAccountActive(senderWallet, "Hesabınız aktif olmadığı için transfer yapamazsınız.");
		// Alıcının hesabı donuk/kapalıysa açıkça söylemek yerine "bulunamadı" diyoruz: aksi halde üçüncü bir
		// kişinin hesap durumu göndericiye ifşa olur (security-reviewer, Faz 5 incelemesi).
		if (receiverWallet.getUser().getStatus() != AccountStatus.ACTIVE) {
			throw new RecipientNotFoundException("Alıcı bulunamadı.");
		}

		applyBalanceChangeInDeadlockSafeOrder(senderWallet, receiverWallet, amount);

		Transfer transfer = new Transfer(senderWallet, receiverWallet, amount, idempotencyKey);
		try {
			return transferRepository.save(transfer);
		}
		catch (DataIntegrityViolationException exception) {
			// Aynı idempotencyKey ile eşzamanlı iki istek, ikisi de yukarıdaki kontrolü "bulunamadı"
			// olarak geçip buraya kadar gelebilir; DB'deki uk_transfers_sender_wallet_idempotency_key
			// kısıtı ikinciyi burada durdurur. Bu durumda mevcut sonucu geri aramak yerine (aynı
			// transaction Postgres tarafından zaten "aborted" duruma düştüğü için yeni bir sorgu
			// çalıştırılamaz) temiz bir 409 döndürüyoruz — client aynı key ile kısa süre sonra tekrar
			// denediğinde ilk isteğin sonucu artık commit edilmiş olacağı için yukarıdaki idempotency
			// kontrolüne düşüp normal şekilde dönecek.
			throw new TransferConflictException(
					"Bu işlem zaten işleniyor ya da işlendi, lütfen kısa süre sonra tekrar deneyin.");
		}
	}

	private void validateAccountActive(Wallet wallet, String message) {
		if (wallet.getUser().getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException(message);
		}
	}

	// Optimistic locking bir UPDATE'in fiziksel satır kilidi almasını engellemiyor — Postgres o satırı
	// yine de transaction commit/rollback'e kadar kilitler. Eğer her zaman "önce gönderen, sonra alıcı"
	// sırasıyla mutasyon uygulayıp flush edersek, ters yönde eşzamanlı bir transfer (B→A) her zaman
	// tam ters sırada kilitlemeye çalışacağı için klasik bir ABBA deadlock'u oluşur. Bunu önlemek için,
	// kimin gönderen kimin alıcı olduğuna bakmadan HER ZAMAN küçük id'li cüzdanı önce kilitliyoruz.
	// (Receiver'ın id'si küçükse credit() debit()'ten önce flush edilir; bu, debit() yetersiz bakiyede
	// hata fırlatırsa receiver'ın flush edilmiş ama henüz commit edilmemiş güncellemesinin transaction
	// rollback'iyle birlikte geri alınmasına güveniyor — bilinçli bir ödünleşim.)
	private void applyBalanceChangeInDeadlockSafeOrder(Wallet senderWallet, Wallet receiverWallet,
			BigDecimal amount) {
		boolean senderHasSmallerId = senderWallet.getId().compareTo(receiverWallet.getId()) < 0;

		if (senderHasSmallerId) {
			senderWallet.debit(amount);
			flushWithConflictHandling();
			receiverWallet.credit(amount);
			flushWithConflictHandling();
		}
		else {
			receiverWallet.credit(amount);
			flushWithConflictHandling();
			senderWallet.debit(amount);
			flushWithConflictHandling();
		}
	}

	private void flushWithConflictHandling() {
		// senderWallet/receiverWallet bu transaction içinde repository'den çekildiği için zaten
		// Hibernate tarafından yönetiliyor (managed); credit()/debit() ile yapılan alan değişiklikleri
		// otomatik "dirty" işaretleniyor, save() çağırmaya gerek yok. flush() burada BİLİNÇLİ olarak
		// elle çağrılıyor: aksi halde UPDATE'ler transaction commit anına kadar ertelenir ve olası bir
		// çakışma bu metot çoktan dönmüşken fırlatılır — o noktada burada yakalayıp anlamlı bir 409'a
		// çeviremeyiz. ConcurrencyFailureException hem @Version çakışmasını (ObjectOptimisticLocking-
		// FailureException) hem de gerçek bir DB deadlock'unu (CannotAcquireLockException) ortak üst
		// sınıfları üzerinden yakalar.
		try {
			walletRepository.flush();
		}
		catch (ConcurrencyFailureException exception) {
			throw new TransferConflictException("İşlem sırasında bir çakışma oluştu, lütfen tekrar deneyin.");
		}
	}

}
