package org.calik.clewa.auth.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.calik.clewa.common.exception.ClewaException;
import org.calik.clewa.common.response.ApiResponse;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler({ UnderageException.class, InvalidPhoneNumberException.class, PhoneAlreadyRegisteredException.class,
			InvalidVerificationCodeException.class })
	public ResponseEntity<ApiResponse<Void>> handleAuthBusinessException(ClewaException exception) {
		ApiResponse<Void> body = ApiResponse.error(exception.getErrorCode(), exception.getMessage());
		return ResponseEntity.badRequest().body(body);
	}

}
