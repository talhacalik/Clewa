package org.calik.clewa.card.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.AccountNotActiveException;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.exception.CardAlreadyExistsException;
import org.calik.clewa.card.exception.CardNotFoundException;
import org.calik.clewa.card.service.CardService;
import org.calik.clewa.common.config.SecurityConfig;
import org.calik.clewa.wallet.entity.Wallet;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
	void getCard_whenUserHasNoCard_returnsNotFound() throws Exception {
		when(cardService.getCardForCurrentUser(USERNAME)).thenThrow(new CardNotFoundException("Kartınız bulunamadı."));

		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CARD_NOT_FOUND"));
	}

}
