package org.calik.clewa.wallet.exception;

import org.calik.clewa.common.exception.ClewaException;

public class TransferConflictException extends ClewaException {

	public TransferConflictException(String message) {
		super("TRANSFER_CONFLICT", message);
	}

}
