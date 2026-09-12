package org.calik.clewa.auth.exception;

import org.calik.clewa.common.exception.ClewaException;

public class InvalidCredentialsException extends ClewaException {

	public InvalidCredentialsException(String message) {
		super("INVALID_CREDENTIALS", message);
	}

}
