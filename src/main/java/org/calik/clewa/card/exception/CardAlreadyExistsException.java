package org.calik.clewa.card.exception;

import org.calik.clewa.common.exception.ClewaException;

public class CardAlreadyExistsException extends ClewaException {

	public CardAlreadyExistsException(String message) {
		super("CARD_ALREADY_EXISTS", message);
	}

}
