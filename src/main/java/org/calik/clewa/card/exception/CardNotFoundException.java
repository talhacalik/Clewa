package org.calik.clewa.card.exception;

import org.calik.clewa.common.exception.ClewaException;

public class CardNotFoundException extends ClewaException {

	public CardNotFoundException(String message) {
		super("CARD_NOT_FOUND", message);
	}

}
