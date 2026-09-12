package org.calik.clewa.auth.exception;

import org.calik.clewa.common.exception.ClewaException;

public class PhoneNotVerifiedException extends ClewaException {

	public PhoneNotVerifiedException(String message) {
		super("PHONE_NOT_VERIFIED", message);
	}

}
