package org.calik.clewa.wallet.exception;

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
public class WalletExceptionHandler {

	@ExceptionHandler(WalletNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(ClewaException exception) {
		ApiResponse<Void> body = ApiResponse.error(exception.getErrorCode(), exception.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

}
