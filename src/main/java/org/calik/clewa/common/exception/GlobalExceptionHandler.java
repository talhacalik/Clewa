package org.calik.clewa.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.calik.clewa.common.response.ApiResponse;

// Bu sınıf sadece beklenmeyen/genel hataları (Exception.class) yakalar; domain'e özel
// *ExceptionHandler sınıfları (AuthExceptionHandler, WalletExceptionHandler, ...) @Order(HIGHEST_PRECEDENCE)
// ile işaretli, bu yüzden Spring onları her zaman burasından önce dener. Bu sınıf kasıtlı olarak
// sırasız bırakılıyor — varsayılan sıra (LOWEST_PRECEDENCE) zaten "en son denenen" anlamına geliyor.
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getAllErrors().get(0).getDefaultMessage();
		ApiResponse<Void> body = ApiResponse.error("VALIDATION_ERROR", message);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		logger.error("Beklenmeyen bir hata oluştu: {}", exception.getMessage(), exception);
		ApiResponse<Void> body = ApiResponse.error("SERVER_ERROR", "Beklenmeyen bir hata oluştu.");
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

}
