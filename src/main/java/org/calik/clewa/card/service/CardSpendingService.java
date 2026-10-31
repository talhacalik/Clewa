package org.calik.clewa.card.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardTransaction;
import org.calik.clewa.card.exception.CardConflictException;
import org.calik.clewa.card.exception.CardNotFoundException;
import org.calik.clewa.card.repository.CardRepository;
import org.calik.clewa.card.repository.CardTransactionRepository;
import org.calik.clewa.common.exception.IdempotencyKeyReuseException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardSpendingService {

	private final CardRepository cardRepository;
	private final CardTransactionRepository cardTransactionRepository;

	// phoneNumber SADECE oturumdaki (Authentication) kullanıcıdan gelmeli, istek gövdesinden asla (TransferService ile aynı
	// invaryant). Kart ve cüzdan bu telefondan bulunur; harcama başkasının kartına/cüzdanına yazılamaz.
	@Transactional
	public CardTransaction spend(String phoneNumber, BigDecimal amount, String merchantName, String idempotencyKey) {
		Card card = cardRepository.findByWallet_User_PhoneNumber(phoneNumber)
			.orElseThrow(() -> new CardNotFoundException("Kartınız bulunamadı."));

		// Aynı anahtarla tekrar gelen istek yeniden işlenmez, ilk sonuç döner (kart başına tekil, bkz. CardTransaction).
		Optional<CardTransaction> existing = cardTransactionRepository.findByCardAndIdempotencyKey(card, idempotencyKey);
		if (existing.isPresent()) {
			CardTransaction previous = existing.get();
			if (previous.getAmount().compareTo(amount) != 0 || !previous.getMerchantName().equals(merchantName)) {
				throw new IdempotencyKeyReuseException(
						"Bu Idempotency-Key daha önce farklı bir harcama için kullanıldı.");
			}
			return previous;
		}

		if (card.getWallet().getUser().getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException("Hesabınız aktif olmadığı için harcama yapamazsınız.");
		}

		// İkisi de aynı transaction'da: biri patlarsa (yetersiz bakiye, limit aşımı) hiçbiri kalıcı olmaz.
		// KİLİT SIRASI KURALI: her zaman önce kart, sonra cüzdan satırı güncellenir (iki ayrı flush ile kodda sabit,
		// Hibernate'in tesadüfi sırasına güvenmiyoruz). Cüzdan kilitliyken asla kart kilitlenmemeli; aksi halde
		// (ör. gelecekte bir iade/hesap kapatma akışı) cards↔wallets arasında ABBA deadlock doğar.
		card.spend(amount);
		flushWithConflictHandling();
		card.getWallet().debit(amount);
		flushWithConflictHandling();

		try {
			return cardTransactionRepository.save(new CardTransaction(card, amount, merchantName, idempotencyKey));
		}
		catch (DataIntegrityViolationException exception) {
			// Aynı anahtarla eşzamanlı iki istek: DB tekilliği ikincisini durdurur; transaction zaten geri alınır.
			throw new CardConflictException("Bu işlem zaten işleniyor ya da işlendi, lütfen kısa süre sonra tekrar deneyin.");
		}
	}

	// Card ve Wallet bu transaction'da yönetilen (managed) entity'ler; flush() onların @Version çakışmasını burada
	// tetikleyip anlamlı bir 409'a çevirmemizi sağlar (aksi halde çakışma metot döndükten sonra, commit'te patlardı).
	private void flushWithConflictHandling() {
		try {
			cardRepository.flush();
		}
		catch (ConcurrencyFailureException exception) {
			throw new CardConflictException("İşlem sırasında bir çakışma oluştu, lütfen tekrar deneyin.");
		}
	}

}
