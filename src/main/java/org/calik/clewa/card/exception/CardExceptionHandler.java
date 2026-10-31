package org.calik.clewa.card.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.calik.clewa.common.exception.ClewaException;
import org.calik.clewa.common.response.ApiResponse;

// @Order(HIGHEST_PRECEDENCE): GlobalExceptionHandler'ın genel Exception yakalayıcısından önce denensin diye
// (bkz. CLAUDE.md §9.1).
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CardExceptionHandler {

	@ExceptionHandler(CardNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(ClewaException exception) {
		return buildResponse(exception, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(DailyLimitExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(ClewaException exception) {
		return buildResponse(exception, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(CardNotActiveException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbidden(ClewaException exception) {
		return buildResponse(exception, HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler({ CardAlreadyExistsException.class, CardConflictException.class })
	public ResponseEntity<ApiResponse<Void>> handleConflict(ClewaException exception) {
		return buildResponse(exception, HttpStatus.CONFLICT);
	}

	private ResponseEntity<ApiResponse<Void>> buildResponse(ClewaException exception, HttpStatus status) {
		ApiResponse<Void> body = ApiResponse.error(exception.getErrorCode(), exception.getMessage());
		return ResponseEntity.status(status).body(body);
	}

}
