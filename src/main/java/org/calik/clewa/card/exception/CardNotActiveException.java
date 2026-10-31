package org.calik.clewa.card.exception;

import org.calik.clewa.common.exception.ClewaException;

public class CardNotActiveException extends ClewaException {

	public CardNotActiveException(String message) {
		super("CARD_NOT_ACTIVE", message);
	}

}
