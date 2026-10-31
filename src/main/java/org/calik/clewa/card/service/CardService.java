package org.calik.clewa.card.service;

import java.math.BigDecimal;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.exception.CardAlreadyExistsException;
import org.calik.clewa.card.exception.CardConflictException;
import org.calik.clewa.card.exception.CardNotFoundException;
import org.calik.clewa.card.repository.CardRepository;
import org.calik.clewa.card.util.CardNumberGenerator;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardService {

	private static final int MAX_CARD_NUMBER_GENERATION_ATTEMPTS = 10;
	private static final String WALLET_UNIQUE_CONSTRAINT = "uk_cards_wallet_id";

	private final CardRepository cardRepository;
	private final WalletService walletService;

	@Transactional
	public Card createCard(String phoneNumber) {
		Wallet wallet = walletService.getWalletForCurrentUser(phoneNumber);

		if (wallet.getUser().getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException("Hesabınız aktif olmadığı için kart oluşturamazsınız.");
		}
		if (cardRepository.existsByWallet(wallet)) {
			throw new CardAlreadyExistsException("Zaten bir kartınız var.");
		}

		Card card = new Card(wallet, generateUniqueCardNumber());
		try {
			return cardRepository.save(card);
		}
		catch (DataIntegrityViolationException exception) {
			// Aynı kullanıcıdan eşzamanlı iki istek, ikisi de yukarıdaki kontrolü geçebilir; DB'deki
			// uk_cards_wallet_id kısıtı ikincisini burada durdurur. save() son işlem olduğu için
			// (aynı transaction'da başka sorgu çalıştırmıyoruz) bunu temiz bir 409'a çevirmek güvenli.
			// Sadece bu kısıt için 409 dönüyoruz; başka bir kısıt ihlali (ör. nadir bir kart numarası
			// çakışması) kullanıcıya yanlış mesaj vermesin diye olduğu gibi yukarı fırlatılır.
			if (isWalletUniqueViolation(exception)) {
				throw new CardAlreadyExistsException("Zaten bir kartınız var.");
			}
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public Card getCardForCurrentUser(String phoneNumber) {
		return findCard(phoneNumber);
	}

	// Dondurmak her zaman serbest (güvenlik için); hesap pasifken bile kullanıcı kartını durdurabilmeli.
	@Transactional
	public Card freezeCard(String phoneNumber) {
		Card card = findCard(phoneNumber);
		card.freeze();
		return flushWithConflictHandling(card);
	}

	@Transactional
	public Card unfreezeCard(String phoneNumber) {
		Card card = findCard(phoneNumber);
		requireActiveAccount(card);
		card.unfreeze();
		return flushWithConflictHandling(card);
	}

	@Transactional
	public Card changeDailyLimit(String phoneNumber, BigDecimal newLimit) {
		Card card = findCard(phoneNumber);
		requireActiveAccount(card);
		card.changeDailyLimit(newLimit);
		return flushWithConflictHandling(card);
	}

	private Card findCard(String phoneNumber) {
		return cardRepository.findByWallet_User_PhoneNumber(phoneNumber)
			.orElseThrow(() -> new CardNotFoundException("Kartınız bulunamadı."));
	}

	private void requireActiveAccount(Card card) {
		if (card.getWallet().getUser().getStatus() != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException("Hesabınız aktif olmadığı için bu işlemi yapamazsınız.");
		}
	}

	// Card zaten bu transaction'da yönetilen (managed) bir entity; değişiklik normalde commit anında yazılır ve
	// @Version çakışması o zaman, metot çoktan dönmüşken fırlar. flush() onu burada tetikleyip anlamlı bir 409'a
	// çevirmemizi sağlar (TransferService ile aynı gerekçe).
	private Card flushWithConflictHandling(Card card) {
		try {
			cardRepository.flush();
		}
		catch (ConcurrencyFailureException exception) {
			throw new CardConflictException("İşlem sırasında bir çakışma oluştu, lütfen tekrar deneyin.");
		}
		return card;
	}

	private boolean isWalletUniqueViolation(DataIntegrityViolationException exception) {
		String message = exception.getMostSpecificCause().getMessage();
		return message != null && message.contains(WALLET_UNIQUE_CONSTRAINT);
	}

	// Önce kontrol edip sonra kaydediyoruz (WalletService.generateUniqueAccountNumber ile aynı gerekçe).
	private String generateUniqueCardNumber() {
		for (int attempt = 0; attempt < MAX_CARD_NUMBER_GENERATION_ATTEMPTS; attempt++) {
			String candidate = CardNumberGenerator.generate();
			if (!cardRepository.existsByCardNumber(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Benzersiz bir kart numarası üretilemedi.");
	}

}
