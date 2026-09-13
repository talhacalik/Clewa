package org.calik.clewa.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.WalletNotFoundException;
import org.calik.clewa.wallet.repository.WalletRepository;
import org.calik.clewa.wallet.util.AccountNumberGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletService {

	private static final int MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS = 10;

	private final WalletRepository walletRepository;

	@Transactional(readOnly = true)
	public Wallet getWalletForCurrentUser(String phoneNumber) {
		return walletRepository.findByUser_PhoneNumber(phoneNumber)
			.orElseThrow(() -> new WalletNotFoundException("Oturumdaki kullanıcının cüzdanı bulunamadı."));
	}

	@Transactional
	public Wallet createWallet(User user) {
		String accountNumber = generateUniqueAccountNumber();
		return walletRepository.save(new Wallet(user, accountNumber));
	}

	// Önce kontrol edip sonra kaydediyoruz (save-and-catch değil): Wallet.id IDENTITY olduğu için
	// save() çağrısı flush'ı hemen tetikliyor — bir UNIQUE ihlali burada oluşursa Hibernate session'ı
	// bu @Transactional metot içinde güvenle tekrar kullanılamaz hale gelir. Check-then-save bu
	// karmaşıklığı (REQUIRES_NEW / yeni EntityManager gerektirmeden) baştan bypass ediyor.
	// Bilinçli kabul edilen kalıntı risk (TOCTOU): kontrol ile kayıt arasında başka bir transaction
	// aynı numarayı üretip commit edebilir. 10^10 olasılık uzayında, bu projenin ölçeğinde pratikte
	// ihmal edilebilir; gerçekleşirse DB'deki uk_wallets_account_number kısıtı (ikinci savunma
	// katmanı) yakalanmamış bir DataIntegrityViolationException'a yol açar → GlobalExceptionHandler
	// bunu 500 olarak döner, transaction temiz rollback olur, veri bütünlüğü bozulmaz.
	private String generateUniqueAccountNumber() {
		for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS; attempt++) {
			String candidate = AccountNumberGenerator.generate();
			if (!walletRepository.existsByAccountNumber(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Benzersiz bir hesap numarası üretilemedi.");
	}

}
