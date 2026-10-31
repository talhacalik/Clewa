package org.calik.clewa.card.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.calik.clewa.AbstractIntegrationTest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.repository.CardRepository;
import org.calik.clewa.card.repository.CardTransactionRepository;
import org.calik.clewa.common.config.JpaAuditingConfig;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.InsufficientBalanceException;
import org.calik.clewa.wallet.repository.WalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @DataJpaTest her testi normalde kendi transaction'ında çalıştırıp sonunda geri alır; bu yüzden servisin transaction'ı
// ona katılır ve GERÇEK bir rollback hiç yaşanmaz. Bu sınıf test transaction'ını kapatır (NOT_SUPPORTED): servis kendi
// transaction'ını açar, hata fırlatınca gerçekten rollback olur ve neyin kalıcı olduğunu veritabanından okuyarak kanıtlarız.
// Veri gerçekten commit edildiği için temizliği elle yapıyoruz.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ JpaAuditingConfig.class, CardSpendingService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CardSpendingRollbackTest extends AbstractIntegrationTest {

	private static final String PHONE = "+905329998877";

	@Autowired
	private CardSpendingService cardSpendingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private CardRepository cardRepository;

	@Autowired
	private CardTransactionRepository cardTransactionRepository;

	@AfterEach
	void cleanUp() {
		cardTransactionRepository.deleteAll();
		cardRepository.deleteAll();
		walletRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void spend_withInsufficientBalance_rollsBackTheAlreadyFlushedCardLimit() {
		User user = new User();
		user.setPhoneNumber(PHONE);
		user.setPasswordHash("hashed-password");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		user.setDateOfBirth(LocalDate.of(1995, 3, 20));
		userRepository.save(user);
		Wallet wallet = new Wallet(user, "9998887776");
		wallet.setBalance(new BigDecimal("100.00"));
		walletRepository.save(wallet);
		cardRepository.save(new Card(wallet, "1111222233334444"));

		// Kart limiti önce flush edilir, cüzdan düşümü sonra patlar: limit düşüşü geri alınmalı.
		assertThatThrownBy(() -> cardSpendingService.spend(PHONE, new BigDecimal("250.00"), "Migros", "rb-key"))
			.isInstanceOf(InsufficientBalanceException.class);

		Card reloaded = cardRepository.findByWallet_User_PhoneNumber(PHONE).orElseThrow();
		assertThat(reloaded.getRemainingDailyLimit()).isEqualByComparingTo("500000.00");
		assertThat(walletRepository.findByUser_PhoneNumber(PHONE).orElseThrow().getBalance())
			.isEqualByComparingTo("100.00");
		assertThat(cardTransactionRepository.count()).isZero();
	}

}
