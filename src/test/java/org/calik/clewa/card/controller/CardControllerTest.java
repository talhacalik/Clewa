package org.calik.clewa.card.controller;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardTransaction;
import org.calik.clewa.card.exception.CardAlreadyExistsException;
import org.calik.clewa.card.exception.CardConflictException;
import org.calik.clewa.card.exception.CardNotActiveException;
import org.calik.clewa.card.exception.CardNotFoundException;
import org.calik.clewa.card.exception.DailyLimitExceededException;
import org.calik.clewa.card.service.CardService;
import org.calik.clewa.card.service.CardSpendingService;
import org.calik.clewa.common.exception.IdempotencyKeyReuseException;
import org.calik.clewa.wallet.exception.InsufficientBalanceException;
import org.calik.clewa.common.config.SecurityConfig;
import org.calik.clewa.wallet.entity.Wallet;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class CardControllerTest {

	private static final String USERNAME = "+905321234567";
	private static final String ENDPOINT = "/api/card";
	private static final String FULL_NUMBER = "1234567890123456";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CardService cardService;

	@MockitoBean
	private CardSpendingService cardSpendingService;

	private Card buildCard() {
		User user = new User();
		user.setPhoneNumber(USERNAME);
		return new Card(new Wallet(user, "1111111111"), FULL_NUMBER);
	}

	@Test
	@WithMockUser(username = USERNAME)
	void createCard_returnsCreatedWithMaskedNumberOnly() throws Exception {
		when(cardService.createCard(USERNAME)).thenReturn(buildCard());

		mockMvc.perform(post(ENDPOINT).with(csrf()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.maskedCardNumber").value("**** **** **** 3456"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andExpect(jsonPath("$.data.dailyLimit").value(500000.00))
			.andExpect(jsonPath("$.data.remainingDailyLimit").value(500000.00))
			.andExpect(content().string(not(containsString(FULL_NUMBER))));
	}

	@Test
	void createCard_withoutAuthentication_isRejected() throws Exception {
		mockMvc.perform(post(ENDPOINT).with(csrf()))
			.andExpect(status().isUnauthorized());

		verify(cardService, never()).createCard(anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void createCard_withoutCsrfToken_isForbidden() throws Exception {
		mockMvc.perform(post(ENDPOINT))
			.andExpect(status().isForbidden());

		verify(cardService, never()).createCard(anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void createCard_whenCardAlreadyExists_returnsConflict() throws Exception {
		when(cardService.createCard(USERNAME)).thenThrow(new CardAlreadyExistsException("Zaten bir kartınız var."));

		mockMvc.perform(post(ENDPOINT).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CARD_ALREADY_EXISTS"));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void createCard_whenAccountNotActive_returnsForbidden() throws Exception {
		when(cardService.createCard(USERNAME)).thenThrow(new AccountNotActiveException("Hesabınız aktif değil."));

		mockMvc.perform(post(ENDPOINT).with(csrf()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_ACTIVE"));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void getCard_returnsMaskedCard() throws Exception {
		when(cardService.getCardForCurrentUser(USERNAME)).thenReturn(buildCard());

		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.maskedCardNumber").value("**** **** **** 3456"))
			.andExpect(content().string(not(containsString(FULL_NUMBER))));
	}

	@Test
	void getCard_withoutAuthentication_isRejected() throws Exception {
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void freezeCard_returnsFrozenCard() throws Exception {
		Card card = buildCard();
		card.freeze();
		when(cardService.freezeCard(USERNAME)).thenReturn(card);

		mockMvc.perform(post(ENDPOINT + "/freeze").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("FROZEN"))
			.andExpect(content().string(not(containsString(FULL_NUMBER))));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void unfreezeCard_returnsActiveCard() throws Exception {
		when(cardService.unfreezeCard(USERNAME)).thenReturn(buildCard());

		mockMvc.perform(post(ENDPOINT + "/unfreeze").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ACTIVE"));
	}

	@Test
	void freezeCard_withoutAuthentication_isRejected() throws Exception {
		mockMvc.perform(post(ENDPOINT + "/freeze").with(csrf()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void freezeCard_withoutCsrfToken_isForbidden() throws Exception {
		mockMvc.perform(post(ENDPOINT + "/freeze"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void freezeCard_whenConflict_returnsConflict() throws Exception {
		when(cardService.freezeCard(USERNAME)).thenThrow(new CardConflictException("Çakışma."));

		mockMvc.perform(post(ENDPOINT + "/freeze").with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CARD_CONFLICT"));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void changeDailyLimit_returnsCardWithNewLimit() throws Exception {
		Card card = buildCard();
		card.changeDailyLimit(new BigDecimal("1000.00"));
		when(cardService.changeDailyLimit(eq(USERNAME), eq(new BigDecimal("1000.00")))).thenReturn(card);

		mockMvc.perform(put(ENDPOINT + "/daily-limit").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dailyLimit\":1000.00}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.dailyLimit").value(1000.00))
			.andExpect(jsonPath("$.data.remainingDailyLimit").value(500000.00));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void changeDailyLimit_withoutLimit_returnsTurkishValidationMessage() throws Exception {
		mockMvc.perform(put(ENDPOINT + "/daily-limit").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Günlük limit zorunludur."));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void changeDailyLimit_withNonPositiveLimit_returnsTurkishValidationMessage() throws Exception {
		mockMvc.perform(put(ENDPOINT + "/daily-limit").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dailyLimit\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Günlük limit sıfırdan büyük olmalıdır."));

		verify(cardService, never()).changeDailyLimit(anyString(), any(BigDecimal.class));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void changeDailyLimit_withTooManyDecimalsOrDigits_isRejected() throws Exception {
		mockMvc.perform(put(ENDPOINT + "/daily-limit").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dailyLimit\":1000.555}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message")
				.value("Günlük limit en fazla 17 tam ve 2 ondalık basamaktan oluşabilir."));

		mockMvc.perform(put(ENDPOINT + "/daily-limit").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dailyLimit\":1e10000000}"))
			.andExpect(status().isBadRequest());

		verify(cardService, never()).changeDailyLimit(anyString(), any(BigDecimal.class));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void changeDailyLimit_aboveMaximum_returnsTurkishValidationMessage() throws Exception {
		mockMvc.perform(put(ENDPOINT + "/daily-limit").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dailyLimit\":10000000.01}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Günlük limit en fazla 10000000.00 olabilir."));

		verify(cardService, never()).changeDailyLimit(anyString(), any(BigDecimal.class));
	}

	@Test
	void changeDailyLimit_withoutAuthentication_isRejected() throws Exception {
		mockMvc.perform(put(ENDPOINT + "/daily-limit").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dailyLimit\":1000.00}"))
			.andExpect(status().isUnauthorized());

		verify(cardService, never()).changeDailyLimit(anyString(), any(BigDecimal.class));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void changeDailyLimit_withoutCsrfToken_isForbidden() throws Exception {
		mockMvc.perform(put(ENDPOINT + "/daily-limit")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dailyLimit\":1000.00}"))
			.andExpect(status().isForbidden());

		verify(cardService, never()).changeDailyLimit(anyString(), any(BigDecimal.class));
	}

	private static final String SPEND = ENDPOINT + "/spend";
	private static final String SPEND_BODY = "{\"amount\":250.00,\"merchantName\":\"Migros\"}";

	private CardTransaction buildTransaction() {
		CardTransaction transaction = new CardTransaction(buildCard(), new BigDecimal("250.00"), "Migros", "k1");
		org.springframework.test.util.ReflectionTestUtils.setField(transaction, "createdAt",
				java.time.Instant.parse("2026-10-31T10:00:00Z"));
		return transaction;
	}

	@Test
	@WithMockUser(username = USERNAME)
	void spend_returnsCreatedReceiptWithoutCardOrBalanceInfo() throws Exception {
		when(cardSpendingService.spend(eq(USERNAME), eq(new BigDecimal("250.00")), eq("Migros"), eq("k1")))
			.thenReturn(buildTransaction());

		mockMvc.perform(post(SPEND).with(csrf())
				.header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(SPEND_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.amount").value(250.00))
			.andExpect(jsonPath("$.data.merchantName").value("Migros"))
			.andExpect(jsonPath("$.data.createdAt").exists())
			.andExpect(jsonPath("$.data.maskedCardNumber").doesNotExist())
			.andExpect(content().string(not(containsString(FULL_NUMBER))));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void spend_userComesFromSessionEvenIfBodyClaimsAnotherUser() throws Exception {
		when(cardSpendingService.spend(eq(USERNAME), any(BigDecimal.class), anyString(), anyString()))
			.thenReturn(buildTransaction());

		mockMvc.perform(post(SPEND).with(csrf())
				.header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":250.00,\"merchantName\":\"Migros\",\"phoneNumber\":\"+905000000000\","
						+ "\"cardNumber\":\"9999999999999999\"}"))
			.andExpect(status().isCreated());

		verify(cardSpendingService).spend(eq(USERNAME), eq(new BigDecimal("250.00")), eq("Migros"), eq("k1"));
	}

	@Test
	void spend_withoutAuthentication_isRejected() throws Exception {
		mockMvc.perform(post(SPEND).with(csrf()).header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON).content(SPEND_BODY))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void spend_withoutCsrfToken_isForbidden() throws Exception {
		mockMvc.perform(post(SPEND).header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON).content(SPEND_BODY))
			.andExpect(status().isForbidden());

		verify(cardSpendingService, never()).spend(anyString(), any(BigDecimal.class), anyString(), anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void spend_withoutIdempotencyKey_returnsBadRequest() throws Exception {
		mockMvc.perform(post(SPEND).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(SPEND_BODY))
			.andExpect(status().isBadRequest());

		verify(cardSpendingService, never()).spend(anyString(), any(BigDecimal.class), anyString(), anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void spend_withTooLongIdempotencyKey_returnsBadRequest() throws Exception {
		mockMvc.perform(post(SPEND).with(csrf()).header("Idempotency-Key", "k".repeat(101))
				.contentType(MediaType.APPLICATION_JSON).content(SPEND_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("Idempotency-Key en fazla 100 karakter olabilir."));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void spend_withInvalidAmounts_areRejectedBeforeReachingService() throws Exception {
		for (String badAmount : new String[] { "-5", "0", "10.005", "1e10000000" }) {
			mockMvc.perform(post(SPEND).with(csrf()).header("Idempotency-Key", "k1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"amount\":" + badAmount + ",\"merchantName\":\"Migros\"}"))
				.andExpect(status().isBadRequest());
		}

		verify(cardSpendingService, never()).spend(anyString(), any(BigDecimal.class), anyString(), anyString());
	}

	@Test
	@WithMockUser(username = USERNAME)
	void spend_withMissingOrBlankOrTooLongMerchant_returnsTurkishMessages() throws Exception {
		mockMvc.perform(post(SPEND).with(csrf()).header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10.00}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("İşyeri adı zorunludur."));

		mockMvc.perform(post(SPEND).with(csrf()).header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10.00,\"merchantName\":\"   \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("İşyeri adı zorunludur."));

		mockMvc.perform(post(SPEND).with(csrf()).header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":10.00,\"merchantName\":\"" + "m".repeat(101) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("İşyeri adı en fazla 100 karakter olabilir."));
	}

	@Test
	@WithMockUser(username = USERNAME)
	void spend_mapsDomainErrorsToStatusCodes() throws Exception {
		when(cardSpendingService.spend(anyString(), any(BigDecimal.class), anyString(), anyString()))
			.thenThrow(new DailyLimitExceededException("Limit."))
			.thenThrow(new CardNotActiveException("Donuk."))
			.thenThrow(new InsufficientBalanceException("Yetersiz."))
			.thenThrow(new CardNotFoundException("Yok."))
			.thenThrow(new CardConflictException("Çakışma."))
			.thenThrow(new IdempotencyKeyReuseException("Farklı harcama."));

		int[] expectedStatuses = { 400, 403, 400, 404, 409, 409 };
		String[] expectedCodes = { "DAILY_LIMIT_EXCEEDED", "CARD_NOT_ACTIVE", "INSUFFICIENT_BALANCE",
				"CARD_NOT_FOUND", "CARD_CONFLICT", "IDEMPOTENCY_KEY_REUSED" };
		for (int i = 0; i < expectedStatuses.length; i++) {
			mockMvc.perform(post(SPEND).with(csrf()).header("Idempotency-Key", "k1")
					.contentType(MediaType.APPLICATION_JSON).content(SPEND_BODY))
				.andExpect(status().is(expectedStatuses[i]))
				.andExpect(jsonPath("$.error.code").value(expectedCodes[i]));
		}
	}

	@Test
	@WithMockUser(username = USERNAME)
	void getCard_whenUserHasNoCard_returnsNotFound() throws Exception {
		when(cardService.getCardForCurrentUser(USERNAME)).thenThrow(new CardNotFoundException("Kartınız bulunamadı."));

		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CARD_NOT_FOUND"));
	}

}
