package org.calik.clewa.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClewaExceptionTest {

	private static class TestClewaException extends ClewaException {
		TestClewaException(String errorCode, String message) {
			super(errorCode, message);
		}
	}

	@Test
	void shouldStoreErrorCodeAndMessage() {
		ClewaException exception = new TestClewaException("TEST_CODE", "Test mesajı");

		assertEquals("TEST_CODE", exception.getErrorCode());
		assertEquals("Test mesajı", exception.getMessage());
	}

	@Test
	void shouldBeAnUncheckedException() {
		ClewaException exception = new TestClewaException("TEST_CODE", "Test mesajı");

		assertTrue(exception instanceof RuntimeException);
	}

}
