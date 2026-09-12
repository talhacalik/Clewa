package org.calik.clewa.auth.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.calik.clewa.auth.dto.LoginRequest;
import org.calik.clewa.auth.dto.LoginResponse;
import org.calik.clewa.auth.dto.SignupRequest;
import org.calik.clewa.auth.dto.SignupResponse;
import org.calik.clewa.auth.dto.VerifyPhoneRequest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.service.LoginService;
import org.calik.clewa.auth.service.PhoneVerificationService;
import org.calik.clewa.auth.service.SignupService;
import org.calik.clewa.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final SignupService signupService;
	private final PhoneVerificationService phoneVerificationService;
	private final LoginService loginService;
	private final SecurityContextRepository securityContextRepository;

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		User user = signupService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SignupResponse.from(user)));
	}

	@PostMapping("/verify-phone")
	public ResponseEntity<ApiResponse<Void>> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
		phoneVerificationService.verify(request);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		User user = loginService.login(request);
		establishSession(user, httpRequest, httpResponse);
		return ResponseEntity.ok(ApiResponse.success(LoginResponse.from(user)));
	}

	private void establishSession(User user, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		// Elle kimlik doğrulaması Spring Security'nin standart filtre zincirini atladığı için, oturum
		// sabitleme (session fixation) koruması da otomatik gelmiyor — bunu burada kendimiz yapıyoruz:
		// giriş öncesinden kalma bir oturum varsa, onun kimliğini değiştirip yenisini veriyoruz.
		if (httpRequest.getSession(false) != null) {
			httpRequest.changeSessionId();
		}

		Authentication authentication = new UsernamePasswordAuthenticationToken(
				user.getPhoneNumber(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
	}

}
