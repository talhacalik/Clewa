package org.calik.clewa.wallet.exception;

import org.calik.clewa.common.exception.ClewaException;

public class WalletNotFoundException extends ClewaException {

	public WalletNotFoundException(String message) {
		super("WALLET_NOT_FOUND", message);
	}

}
