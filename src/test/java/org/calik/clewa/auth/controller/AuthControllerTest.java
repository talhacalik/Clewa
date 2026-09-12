package org.calik.clewa.auth.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import org.calik.clewa.auth.dto.SignupRequest;
import org.calik.clewa.auth.dto.VerifyPhoneRequest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.InvalidVerificationCodeException;
import org.calik.clewa.auth.exception.PhoneAlreadyRegisteredException;
import org.calik.clewa.auth.service.PhoneVerificationService;
import org.calik.clewa.auth.service.SignupService;
import org.calik.clewa.common.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private SignupService signupService;

	@MockitoBean
	private PhoneVerificationService phoneVerificationService;

	private SignupRequest validRequest() {
		return new SignupRequest("5321234567", "Password1", "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));
	}

	@Test
	void signup_withValidRequest_returns201WithUserData() throws Exception {
		User savedUser = new User();
		savedUser.setPhoneNumber("+905321234567");
		savedUser.setFirstName("Ahmet");
		savedUser.setLastName("Yılmaz");
		when(signupService.signup(any(SignupRequest.class))).thenReturn(savedUser);

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.phoneNumber").value("+905321234567"))
			.andExpect(jsonPath("$.data.firstName").value("Ahmet"));
	}

	@Test
	void signup_withBlankPhoneNumber_returns400() throws Exception {
		SignupRequest invalidRequest = new SignupRequest("", "Password1", "Ahmet", "Yılmaz", LocalDate.of(1995, 3, 20));

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidRequest)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void signup_whenPhoneAlreadyRegistered_returns400WithErrorCode() throws Exception {
		when(signupService.signup(any(SignupRequest.class)))
			.thenThrow(new PhoneAlreadyRegisteredException("Bu telefon numarası zaten kayıtlı."));

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validRequest())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_REGISTERED"));
	}

	@Test
	void verifyPhone_withValidRequest_returns200() throws Exception {
		VerifyPhoneRequest request = new VerifyPhoneRequest("5321234567", "123456");

		mockMvc.perform(post("/api/auth/verify-phone")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		verify(phoneVerificationService).verify(request);
	}

	@Test
	void verifyPhone_withInvalidCode_returns400WithErrorCode() throws Exception {
		VerifyPhoneRequest request = new VerifyPhoneRequest("5321234567", "000000");
		doThrow(new InvalidVerificationCodeException("Doğrulama kodu geçersiz veya süresi dolmuş."))
			.when(phoneVerificationService).verify(request);

		mockMvc.perform(post("/api/auth/verify-phone")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_VERIFICATION_CODE"));
	}

}
