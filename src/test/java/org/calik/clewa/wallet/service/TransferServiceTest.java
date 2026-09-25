package org.calik.clewa.wallet.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.wallet.entity.Transfer;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.InsufficientBalanceException;
import org.calik.clewa.wallet.exception.RecipientNotFoundException;
import org.calik.clewa.wallet.exception.SelfTransferException;
import org.calik.clewa.wallet.exception.TransferConflictException;
import org.calik.clewa.wallet.repository.TransferRepository;
import org.calik.clewa.wallet.repository.WalletRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

	private static final String SENDER_PHONE = "+905321234567";
	private static final String RECEIVER_PHONE = "+905339876543";
	private static final String SENDER_ACCOUNT = "1111111111";
	private static final String RECEIVER_ACCOUNT = "2222222222";
	private static final String IDEMPOTENCY_KEY = "idempotency-key-1";

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private TransferRepository transferRepository;

	@Mock
	private WalletService walletService;

	@InjectMocks
	private TransferService transferService;

	private Wallet buildWallet(Long id, String phoneNumber, String accountNumber, String balance) {
		User user = new User();
		user.setPhoneNumber(phoneNumber);
		Wallet wallet = new Wallet(user, accountNumber);
		wallet.setBalance(new BigDecimal(balance));
		ReflectionTestUtils.setField(wallet, "id", id);
		return wallet;
	}

	private void stubNoExistingTransfer(Wallet sender) {
		when(transferRepository.findBySenderWalletAndIdempotencyKey(sender, IDEMPOTENCY_KEY))
			.thenReturn(Optional.empty());
	}

	@Test
	void transfer_whenIdempotencyKeyAlreadyUsedBySameSender_returnsExistingTransferWithoutReprocessing() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		Transfer existingTransfer = mock(Transfer.class);
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		when(transferRepository.findBySenderWalletAndIdempotencyKey(sender, IDEMPOTENCY_KEY))
			.thenReturn(Optional.of(existingTransfer));

		Transfer result = transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"),
				IDEMPOTENCY_KEY);

		assertThat(result).isEqualTo(existingTransfer);
		verify(walletRepository, never()).findByAccountNumber(anyString());
		verify(transferRepository, never()).save(any());
	}

	@Test
	void transfer_movesBalanceAndSavesTransfer() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		Wallet receiver = buildWallet(2L, RECEIVER_PHONE, RECEIVER_ACCOUNT, "20.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber(RECEIVER_ACCOUNT)).thenReturn(Optional.of(receiver));
		when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Transfer result = transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"),
				IDEMPOTENCY_KEY);

		assertThat(sender.getBalance()).isEqualByComparingTo("70.00");
		assertThat(receiver.getBalance()).isEqualByComparingTo("50.00");
		assertThat(result.getSenderWallet()).isEqualTo(sender);
		assertThat(result.getReceiverWallet()).isEqualTo(receiver);
		assertThat(result.getAmount()).isEqualByComparingTo("30.00");
		verify(walletRepository, times(2)).flush();
	}

	@Test
	void transfer_whenRecipientAccountNumberNotFound_throwsRecipientNotFoundException() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber("9999999999")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transferService.transfer(SENDER_PHONE, "9999999999", new BigDecimal("30.00"),
				IDEMPOTENCY_KEY))
			.isInstanceOf(RecipientNotFoundException.class);

		verify(transferRepository, never()).save(any());
	}

	@Test
	void transfer_whenRecipientIsOwnAccountNumber_throwsSelfTransferExceptionWithoutQuerying() {
		Wallet ownWallet = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(ownWallet);
		stubNoExistingTransfer(ownWallet);

		assertThatThrownBy(() -> transferService.transfer(SENDER_PHONE, SENDER_ACCOUNT, new BigDecimal("30.00"),
				IDEMPOTENCY_KEY))
			.isInstanceOf(SelfTransferException.class);

		assertThat(ownWallet.getBalance()).isEqualByComparingTo("100.00");
		verify(walletRepository, never()).findByAccountNumber(anyString());
		verify(transferRepository, never()).save(any());
	}

	@Test
	void transfer_whenSenderHasInsufficientBalance_throwsInsufficientBalanceException() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "10.00");
		Wallet receiver = buildWallet(2L, RECEIVER_PHONE, RECEIVER_ACCOUNT, "20.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber(RECEIVER_ACCOUNT)).thenReturn(Optional.of(receiver));

		assertThatThrownBy(() -> transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"),
				IDEMPOTENCY_KEY))
			.isInstanceOf(InsufficientBalanceException.class);

		assertThat(receiver.getBalance()).isEqualByComparingTo("20.00");
		verify(transferRepository, never()).save(any());
	}

	@Test
	void transfer_whenSenderAccountNotActive_throwsAccountNotActiveExceptionWithoutMovingBalance() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		sender.getUser().setStatus(AccountStatus.FROZEN);
		Wallet receiver = buildWallet(2L, RECEIVER_PHONE, RECEIVER_ACCOUNT, "20.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber(RECEIVER_ACCOUNT)).thenReturn(Optional.of(receiver));

		assertThatThrownBy(() -> transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"),
				IDEMPOTENCY_KEY))
			.isInstanceOf(AccountNotActiveException.class);

		assertThat(sender.getBalance()).isEqualByComparingTo("100.00");
		assertThat(receiver.getBalance()).isEqualByComparingTo("20.00");
		verify(transferRepository, never()).save(any());
	}

	@Test
	void transfer_whenReceiverAccountNotActive_throwsRecipientNotFoundWithoutRevealingStatus() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		Wallet receiver = buildWallet(2L, RECEIVER_PHONE, RECEIVER_ACCOUNT, "20.00");
		receiver.getUser().setStatus(AccountStatus.CLOSED);
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber(RECEIVER_ACCOUNT)).thenReturn(Optional.of(receiver));

		assertThatThrownBy(() -> transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"),
				IDEMPOTENCY_KEY))
			.isInstanceOf(RecipientNotFoundException.class);

		assertThat(sender.getBalance()).isEqualByComparingTo("100.00");
		assertThat(receiver.getBalance()).isEqualByComparingTo("20.00");
		verify(transferRepository, never()).save(any());
	}

	@Test
	void transfer_whenOptimisticLockConflictOccurs_throwsTransferConflictException() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		Wallet receiver = buildWallet(2L, RECEIVER_PHONE, RECEIVER_ACCOUNT, "20.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber(RECEIVER_ACCOUNT)).thenReturn(Optional.of(receiver));
		doThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L)).when(walletRepository).flush();

		assertThatThrownBy(() -> transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"),
				IDEMPOTENCY_KEY))
			.isInstanceOf(TransferConflictException.class);

		verify(transferRepository, never()).save(any());
	}

	@Test
	void transfer_whenSaveHitsDuplicateIdempotencyKeyRace_throwsTransferConflictException() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		Wallet receiver = buildWallet(2L, RECEIVER_PHONE, RECEIVER_ACCOUNT, "20.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber(RECEIVER_ACCOUNT)).thenReturn(Optional.of(receiver));
		when(transferRepository.save(any(Transfer.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

		assertThatThrownBy(() -> transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"),
				IDEMPOTENCY_KEY))
			.isInstanceOf(TransferConflictException.class);
	}

	@Test
	void transfer_whenSenderHasSmallerId_flushesSenderDebitBeforeReceiverCredit() {
		Wallet sender = buildWallet(1L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		Wallet receiver = buildWallet(2L, RECEIVER_PHONE, RECEIVER_ACCOUNT, "20.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber(RECEIVER_ACCOUNT)).thenReturn(Optional.of(receiver));
		when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

		List<BigDecimal> senderBalanceAtEachFlush = new ArrayList<>();
		List<BigDecimal> receiverBalanceAtEachFlush = new ArrayList<>();
		doAnswer(invocation -> {
			senderBalanceAtEachFlush.add(sender.getBalance());
			receiverBalanceAtEachFlush.add(receiver.getBalance());
			return null;
		}).when(walletRepository).flush();

		transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"), IDEMPOTENCY_KEY);

		// sender'ın id'si küçük: ilk flush'ta sender'ın bakiyesi zaten düşmüş, receiver'ınki henüz değişmemiş olmalı.
		assertThat(senderBalanceAtEachFlush.get(0)).isEqualByComparingTo("70.00");
		assertThat(receiverBalanceAtEachFlush.get(0)).isEqualByComparingTo("20.00");
		assertThat(senderBalanceAtEachFlush.get(1)).isEqualByComparingTo("70.00");
		assertThat(receiverBalanceAtEachFlush.get(1)).isEqualByComparingTo("50.00");
	}

	@Test
	void transfer_whenReceiverHasSmallerId_flushesReceiverCreditBeforeSenderDebit() {
		Wallet sender = buildWallet(2L, SENDER_PHONE, SENDER_ACCOUNT, "100.00");
		Wallet receiver = buildWallet(1L, RECEIVER_PHONE, RECEIVER_ACCOUNT, "20.00");
		when(walletService.getWalletForCurrentUser(SENDER_PHONE)).thenReturn(sender);
		stubNoExistingTransfer(sender);
		when(walletRepository.findByAccountNumber(RECEIVER_ACCOUNT)).thenReturn(Optional.of(receiver));
		when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

		List<BigDecimal> senderBalanceAtEachFlush = new ArrayList<>();
		List<BigDecimal> receiverBalanceAtEachFlush = new ArrayList<>();
		doAnswer(invocation -> {
			senderBalanceAtEachFlush.add(sender.getBalance());
			receiverBalanceAtEachFlush.add(receiver.getBalance());
			return null;
		}).when(walletRepository).flush();

		transferService.transfer(SENDER_PHONE, RECEIVER_ACCOUNT, new BigDecimal("30.00"), IDEMPOTENCY_KEY);

		// receiver'ın id'si küçük: ilk flush'ta receiver'ın bakiyesi zaten artmış, sender'ınki henüz değişmemiş olmalı.
		assertThat(receiverBalanceAtEachFlush.get(0)).isEqualByComparingTo("50.00");
		assertThat(senderBalanceAtEachFlush.get(0)).isEqualByComparingTo("100.00");
		assertThat(receiverBalanceAtEachFlush.get(1)).isEqualByComparingTo("50.00");
		assertThat(senderBalanceAtEachFlush.get(1)).isEqualByComparingTo("70.00");
	}

}
