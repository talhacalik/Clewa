package org.calik.clewa.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.calik.clewa.auth.dto.SignupRequest;
import org.calik.clewa.auth.dto.SignupResponse;
import org.calik.clewa.auth.dto.VerifyPhoneRequest;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.auth.service.PhoneVerificationService;
import org.calik.clewa.auth.service.SignupService;
import org.calik.clewa.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final SignupService signupService;
	private final PhoneVerificationService phoneVerificationService;

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

}
