package org.calik.clewa.wallet.controller;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.common.config.SecurityConfig;
import org.calik.clewa.wallet.entity.Transfer;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.exception.InsufficientBalanceException;
import org.calik.clewa.wallet.exception.RecipientNotFoundException;
import org.calik.clewa.wallet.exception.SelfTransferException;
import org.calik.clewa.wallet.exception.TransferConflictException;
import org.calik.clewa.wallet.service.TransferService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class TransferControllerTest {

	private static final String USERNAME = "+905321234567";
	private static final String ENDPOINT = "/api/transfers";
	private static final String KEY = "idem-key-1";
	private static final String VALID_BODY = "{\"recipientAccountNumber\":\"2222222222\",\"amount\":30.00}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TransferService transferService;

	private Transfer buildTransfer() {
		User senderUser = new User();
		senderUser.setPhoneNumber(USERNAME);
		User receiverUser = new User();
		receiverUser.setPhoneNumber("+905339876543");
		Wallet sender = new Wallet(senderUser, "1111111111");
		Wallet receiver = new Wallet(receiverUser, "2222222222");
		Transfer transfer = new Transfer(sender, receiver, new BigDecimal("30.00"), KEY);
		ReflectionTestUtils.setField(transfer, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
		return transfer;
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withValidRequest_returnsCreatedReceipt() throws Exception {
		when(transferService.transfer(eq(USERNAME), eq("2222222222"), eq(new BigDecimal("30.00")), eq(KEY)))
			.thenReturn(buildTransfer());

		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.recipientAccountNumber").value("2222222222"))
			.andExpect(jsonPath("$.data.amount").value(30.00))
			.andExpect(jsonPath("$.data.createdAt").exists());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_senderComesFromSessionEvenIfBodyClaimsAnotherSender() throws Exception {
		when(transferService.transfer(eq(USERNAME), anyString(), any(BigDecimal.class), anyString()))
			.thenReturn(buildTransfer());
		String bodyWithFakeSender = "{\"recipientAccountNumber\":\"2222222222\",\"amount\":30.00,"
				+ "\"senderPhoneNumber\":\"+905000000000\"}";

		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(bodyWithFakeSender))
			.andExpect(status().isCreated());

		verify(transferService).transfer(eq(USERNAME), eq("2222222222"), eq(new BigDecimal("30.00")), eq(KEY));
	}

	@Test
	void transfer_withoutAuthentication_isRejected() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isUnauthorized());

		verify(transferService, never()).transfer(anyString(), anyString(), any(BigDecimal.class), anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withoutCsrfToken_isForbidden() throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withoutIdempotencyKeyHeader_returnsBadRequest() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.message").value("Idempotency-Key başlığı zorunludur."));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withBlankIdempotencyKey_returnsBadRequest() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", "   ")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Idempotency-Key başlığı zorunludur."));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withTooLongIdempotencyKey_returnsBadRequest() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", "k".repeat(101))
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Idempotency-Key en fazla 100 karakter olabilir."));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withoutAccountNumber_returnsTurkishValidationMessage() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":30.00}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.error.message").value("Hesap numarası zorunludur."));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withoutAmount_returnsTurkishValidationMessage() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientAccountNumber\":\"2222222222\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Gönderilecek tutar zorunludur."));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withNegativeAmount_returnsTurkishValidationMessage() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientAccountNumber\":\"2222222222\",\"amount\":-50}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Gönderilecek tutar pozitif olmalıdır."));

		verify(transferService, never()).transfer(anyString(), anyString(), any(BigDecimal.class), anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withNonNumericAmount_returnsBadRequest() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientAccountNumber\":\"2222222222\",\"amount\":\"abc\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_whenBalanceInsufficient_returnsBadRequest() throws Exception {
		when(transferService.transfer(anyString(), anyString(), any(BigDecimal.class), anyString()))
			.thenThrow(new InsufficientBalanceException("Yetersiz bakiye."));

		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INSUFFICIENT_BALANCE"));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_whenRecipientNotFound_returnsNotFound() throws Exception {
		when(transferService.transfer(anyString(), anyString(), any(BigDecimal.class), anyString()))
			.thenThrow(new RecipientNotFoundException("Alıcı bulunamadı."));

		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECIPIENT_NOT_FOUND"));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_whenConflictOccurs_returnsConflict() throws Exception {
		when(transferService.transfer(anyString(), anyString(), any(BigDecimal.class), anyString()))
			.thenThrow(new TransferConflictException("İşlem sırasında bir çakışma oluştu."));

		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("TRANSFER_CONFLICT"));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_whenSelfTransfer_returnsBadRequest() throws Exception {
		when(transferService.transfer(anyString(), anyString(), any(BigDecimal.class), anyString()))
			.thenThrow(new SelfTransferException("Kendi hesabınıza transfer yapamazsınız."));

		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("SELF_TRANSFER_NOT_ALLOWED"));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_whenSenderAccountNotActive_returnsForbidden() throws Exception {
		when(transferService.transfer(anyString(), anyString(), any(BigDecimal.class), anyString()))
			.thenThrow(new AccountNotActiveException("Hesabınız aktif olmadığı için transfer yapamazsınız."));

		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_ACTIVE"));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withMoreThanTwoDecimalPlaces_isRejectedBeforeReachingService() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientAccountNumber\":\"2222222222\",\"amount\":0.015}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message")
				.value("Gönderilecek tutar en fazla 17 tam ve 2 ondalık basamaktan oluşabilir."));

		verify(transferService, never()).transfer(anyString(), anyString(), any(BigDecimal.class), anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withAbsurdlyLargeExponentAmount_isRejectedBeforeReachingService() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientAccountNumber\":\"2222222222\",\"amount\":1e10000000}"))
			.andExpect(status().isBadRequest());

		verify(transferService, never()).transfer(anyString(), anyString(), any(BigDecimal.class), anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withMalformedAccountNumber_returnsTurkishValidationMessage() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientAccountNumber\":\"12345\",\"amount\":30.00}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Hesap numarası 10 haneli bir sayı olmalıdır."));

		verify(transferService, never()).transfer(anyString(), anyString(), any(BigDecimal.class), anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void transfer_withMalformedJson_returnsInvalidRequestCode() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf())
				.header("Idempotency-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
	}

}
