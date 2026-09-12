package org.calik.clewa.auth.exception;

import org.calik.clewa.common.ClewaException;

public class InvalidPhoneNumberException extends ClewaException {

	public InvalidPhoneNumberException(String message) {
		super("INVALID_PHONE_NUMBER", message);
	}

}
