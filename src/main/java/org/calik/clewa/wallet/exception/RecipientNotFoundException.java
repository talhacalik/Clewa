package org.calik.clewa.wallet.exception;

import org.calik.clewa.common.exception.ClewaException;

public class RecipientNotFoundException extends ClewaException {

	public RecipientNotFoundException(String message) {
		super("RECIPIENT_NOT_FOUND", message);
	}

}
