package org.calik.clewa.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.calik.clewa.common.response.ApiResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void handleUnexpectedException_shouldReturnServerErrorResponse() {
		Exception exception = new RuntimeException("beklenmeyen bir arıza");

		ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpectedException(exception);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		ApiResponse<Void> body = response.getBody();
		assertNotNull(body);
		assertFalse(body.success());
		assertEquals("SERVER_ERROR", body.error().code());
	}

}
