package org.calik.clewa.wallet.exception;

import org.calik.clewa.common.exception.ClewaException;

public class SelfTransferException extends ClewaException {

	public SelfTransferException(String message) {
		super("SELF_TRANSFER_NOT_ALLOWED", message);
	}

}
