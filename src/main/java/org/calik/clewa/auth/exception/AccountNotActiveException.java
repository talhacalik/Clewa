package org.calik.clewa.auth.exception;

import org.calik.clewa.common.exception.ClewaException;

public class AccountNotActiveException extends ClewaException {

	public AccountNotActiveException(String message) {
		super("ACCOUNT_NOT_ACTIVE", message);
	}

}
