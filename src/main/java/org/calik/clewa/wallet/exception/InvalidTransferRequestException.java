package org.calik.clewa.wallet.exception;

import org.calik.clewa.common.exception.ClewaException;

public class InvalidTransferRequestException extends ClewaException {

	public InvalidTransferRequestException(String message) {
		super("INVALID_TRANSFER_REQUEST", message);
	}

}
