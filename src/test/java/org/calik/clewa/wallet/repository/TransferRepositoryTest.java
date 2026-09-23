package org.calik.clewa.wallet.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import org.calik.clewa.AbstractIntegrationTest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.repository.UserRepository;
import org.calik.clewa.common.config.JpaAuditingConfig;
import org.calik.clewa.wallet.entity.Transfer;
import org.calik.clewa.wallet.entity.Wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class TransferRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private TransferRepository transferRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private UserRepository userRepository;

	private Wallet persistedWallet(String phoneNumber, String accountNumber) {
		User user = new User();
		user.setPhoneNumber(phoneNumber);
		user.setPasswordHash("hashed-password");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		user.setDateOfBirth(LocalDate.of(1995, 3, 20));
		userRepository.save(user);
		return walletRepository.save(new Wallet(user, accountNumber));
	}

	@Test
	void save_shouldPersistTransferWithAuditFields() {
		Wallet sender = persistedWallet("+905321234567", "1111111111");
		Wallet receiver = persistedWallet("+905339876543", "2222222222");

		Transfer transfer = new Transfer(sender, receiver, new BigDecimal("50.00"), "idem-key-1");
		Transfer saved = transferRepository.save(transfer);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getSenderWallet().getId()).isEqualTo(sender.getId());
		assertThat(saved.getReceiverWallet().getId()).isEqualTo(receiver.getId());
		assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
		assertThat(saved.getIdempotencyKey()).isEqualTo("idem-key-1");
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	@Test
	void findBySenderWalletAndIdempotencyKey_shouldReturnSavedTransfer() {
		Wallet sender = persistedWallet("+905321234567", "1111111111");
		Wallet receiver = persistedWallet("+905339876543", "2222222222");
		transferRepository.save(new Transfer(sender, receiver, new BigDecimal("25.00"), "idem-key-2"));

		var found = transferRepository.findBySenderWalletAndIdempotencyKey(sender, "idem-key-2");

		assertThat(found).isPresent();
		assertThat(found.get().getAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
	}

	@Test
	void findBySenderWalletAndIdempotencyKey_whenNotFound_returnsEmpty() {
		Wallet sender = persistedWallet("+905321234567", "1111111111");

		var found = transferRepository.findBySenderWalletAndIdempotencyKey(sender, "does-not-exist");

		assertThat(found).isEmpty();
	}

	@Test
	void findBySenderWalletAndIdempotencyKey_whenSameKeyUsedByDifferentSender_returnsEmpty() {
		Wallet firstSender = persistedWallet("+905321234567", "1111111111");
		Wallet secondSender = persistedWallet("+905339876543", "2222222222");
		Wallet receiver = persistedWallet("+905351112233", "3333333333");
		transferRepository.save(new Transfer(firstSender, receiver, new BigDecimal("25.00"), "shared-key"));

		var found = transferRepository.findBySenderWalletAndIdempotencyKey(secondSender, "shared-key");

		assertThat(found).isEmpty();
	}

	@Test
	void save_whenIdempotencyKeyDuplicateForSameSender_violatesUniqueConstraint() {
		Wallet sender = persistedWallet("+905321234567", "1111111111");
		Wallet receiver = persistedWallet("+905339876543", "2222222222");
		transferRepository.saveAndFlush(new Transfer(sender, receiver, new BigDecimal("10.00"), "duplicate-key"));

		assertThatThrownBy(() -> transferRepository
			.saveAndFlush(new Transfer(sender, receiver, new BigDecimal("20.00"), "duplicate-key")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_whenIdempotencyKeySharedByDifferentSenders_isAllowed() {
		Wallet firstSender = persistedWallet("+905321234567", "1111111111");
		Wallet secondSender = persistedWallet("+905339876543", "2222222222");
		Wallet receiver = persistedWallet("+905351112233", "3333333333");
		transferRepository.saveAndFlush(new Transfer(firstSender, receiver, new BigDecimal("10.00"), "shared-key"));

		Transfer secondTransfer = transferRepository
			.saveAndFlush(new Transfer(secondSender, receiver, new BigDecimal("20.00"), "shared-key"));

		assertThat(secondTransfer.getId()).isNotNull();
	}

	@Test
	void save_whenAmountIsNotPositive_violatesCheckConstraint() {
		Wallet sender = persistedWallet("+905321234567", "1111111111");
		Wallet receiver = persistedWallet("+905339876543", "2222222222");
		Transfer transfer = new Transfer(sender, receiver, new BigDecimal("0.00"), "idem-key-3");

		assertThatThrownBy(() -> transferRepository.saveAndFlush(transfer))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_whenSenderWalletIsNull_violatesNotNullConstraint() {
		Wallet receiver = persistedWallet("+905339876543", "2222222222");
		Transfer transfer = new Transfer(null, receiver, new BigDecimal("10.00"), "idem-key-4");

		assertThatThrownBy(() -> transferRepository.saveAndFlush(transfer))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void save_whenReceiverWalletIsNull_violatesNotNullConstraint() {
		Wallet sender = persistedWallet("+905321234567", "1111111111");
		Transfer transfer = new Transfer(sender, null, new BigDecimal("10.00"), "idem-key-5");

		assertThatThrownBy(() -> transferRepository.saveAndFlush(transfer))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
