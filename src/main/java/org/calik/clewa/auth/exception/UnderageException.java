package org.calik.clewa.auth.exception;

import org.calik.clewa.common.exception.ClewaException;

public class UnderageException extends ClewaException {

	public UnderageException(String message) {
		super("MINIMUM_AGE_REQUIRED", message);
	}

}
