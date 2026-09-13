package org.calik.clewa.wallet.exception;

import org.calik.clewa.common.exception.ClewaException;

public class InsufficientBalanceException extends ClewaException {

	public InsufficientBalanceException(String message) {
		super("INSUFFICIENT_BALANCE", message);
	}

}
