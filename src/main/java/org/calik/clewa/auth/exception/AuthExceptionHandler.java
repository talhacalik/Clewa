package org.calik.clewa.auth.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.calik.clewa.common.exception.ClewaException;
import org.calik.clewa.common.response.ApiResponse;

// @Order(HIGHEST_PRECEDENCE): Spring, @RestControllerAdvice sınıflarını @Order olmadan keyfi bir
// sırayla (paket/bean adına göre) dener; bu domain'e özel handler'ın GlobalExceptionHandler'ın
// genel Exception.class yakalayıcısından önce denenmesini garanti eder.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

	@ExceptionHandler({ UnderageException.class, InvalidPhoneNumberException.class, PhoneAlreadyRegisteredException.class,
			InvalidVerificationCodeException.class })
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(ClewaException exception) {
		return buildResponse(exception, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnauthorized(ClewaException exception) {
		return buildResponse(exception, HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler({ AccountNotActiveException.class, PhoneNotVerifiedException.class })
	public ResponseEntity<ApiResponse<Void>> handleForbidden(ClewaException exception) {
		return buildResponse(exception, HttpStatus.FORBIDDEN);
	}

	private ResponseEntity<ApiResponse<Void>> buildResponse(ClewaException exception, HttpStatus status) {
		ApiResponse<Void> body = ApiResponse.error(exception.getErrorCode(), exception.getMessage());
		return ResponseEntity.status(status).body(body);
	}

}
