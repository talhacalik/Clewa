package org.calik.clewa.auth.exception;

import org.calik.clewa.common.exception.ClewaException;

public class InvalidPhoneNumberException extends ClewaException {

	public InvalidPhoneNumberException(String message) {
		super("INVALID_PHONE_NUMBER", message);
	}

}
