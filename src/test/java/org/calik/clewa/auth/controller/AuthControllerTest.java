package org.calik.clewa.auth.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import org.calik.clewa.auth.dto.LoginRequest;
import org.calik.clewa.auth.dto.SignupRequest;
import org.calik.clewa.auth.dto.VerifyPhoneRequest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.exception.InvalidCredentialsException;
import org.calik.clewa.auth.exception.InvalidVerificationCodeException;
import org.calik.clewa.auth.exception.PhoneAlreadyRegisteredException;
import org.calik.clewa.auth.exception.PhoneNotVerifiedException;
import org.calik.clewa.auth.service.LoginService;
import org.calik.clewa.auth.service.PhoneVerificationService;
import org.calik.clewa.auth.service.SignupService;
import org.calik.clewa.common.config.SecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;
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

	@MockitoBean
	private LoginService loginService;

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

	@Test
	void login_withValidCredentials_returns200AndEstablishesSession() throws Exception {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		when(loginService.login(any(LoginRequest.class))).thenReturn(user);

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest("5321234567", "Password1"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.phoneNumber").value("+905321234567"))
			.andExpect(SecurityMockMvcResultMatchers.authenticated());
	}

	@Test
	void login_withInvalidCredentials_returns401() throws Exception {
		when(loginService.login(any(LoginRequest.class)))
			.thenThrow(new InvalidCredentialsException("Telefon numarası veya şifre hatalı."));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest("5321234567", "WrongPassword1"))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
			.andExpect(SecurityMockMvcResultMatchers.unauthenticated());
	}

	@Test
	void login_withUnverifiedPhone_returns403() throws Exception {
		when(loginService.login(any(LoginRequest.class)))
			.thenThrow(new PhoneNotVerifiedException("Giriş yapmadan önce telefon numaranızı doğrulamalısınız."));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest("5321234567", "Password1"))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("PHONE_NOT_VERIFIED"));
	}

	@Test
	void login_withPreExistingSession_rotatesSessionId() throws Exception {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		when(loginService.login(any(LoginRequest.class))).thenReturn(user);

		MockHttpSession existingSession = new MockHttpSession();
		String originalSessionId = existingSession.getId();

		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest("5321234567", "Password1")))
				.session(existingSession))
			.andExpect(status().isOk())
			.andReturn();

		String newSessionId = result.getRequest().getSession(false).getId();
		assertThat(newSessionId).isNotEqualTo(originalSessionId);
	}

}
