package org.calik.clewa.auth.exception;

import org.calik.clewa.common.exception.ClewaException;

public class PhoneAlreadyRegisteredException extends ClewaException {

	public PhoneAlreadyRegisteredException(String message) {
		super("PHONE_ALREADY_REGISTERED", message);
	}

}
