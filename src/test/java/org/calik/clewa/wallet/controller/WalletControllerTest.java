package org.calik.clewa.wallet.controller;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.common.config.SecurityConfig;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.service.WalletService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class WalletControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WalletService walletService;

	@Test
	@WithMockUser(username = "+905321234567")
	void getWallet_withAuthenticatedUser_returnsBalance() throws Exception {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		Wallet wallet = new Wallet(user);
		wallet.setBalance(new BigDecimal("0.00"));
		when(walletService.getWalletForCurrentUser("+905321234567")).thenReturn(wallet);

		mockMvc.perform(get("/api/wallet"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.balance").value(0.00));
	}

	@Test
	void getWallet_withoutAuthentication_isRejected() throws Exception {
		mockMvc.perform(get("/api/wallet"))
			.andExpect(status().isUnauthorized());
	}

}
