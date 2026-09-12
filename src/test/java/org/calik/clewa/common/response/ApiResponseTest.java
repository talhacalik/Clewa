package org.calik.clewa.common.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

	@Test
	void success_shouldWrapDataAsSuccessfulResponse() {
		ApiResponse<String> response = ApiResponse.success("merhaba");

		assertTrue(response.success());
		assertEquals("merhaba", response.data());
		assertNull(response.error());
		assertNotNull(response.timestamp());
	}

	@Test
	void error_shouldWrapErrorDetailsAsFailedResponse() {
		ApiResponse<Object> response = ApiResponse.error("INVALID_INPUT", "Geçersiz giriş");

		assertFalse(response.success());
		assertNull(response.data());
		assertEquals("INVALID_INPUT", response.error().code());
		assertEquals("Geçersiz giriş", response.error().message());
		assertNotNull(response.timestamp());
	}

}
