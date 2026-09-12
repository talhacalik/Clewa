package org.calik.clewa.auth.exception;

import org.calik.clewa.common.exception.ClewaException;

public class InvalidVerificationCodeException extends ClewaException {

	public InvalidVerificationCodeException(String message) {
		super("INVALID_VERIFICATION_CODE", message);
	}

}
