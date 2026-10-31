package org.calik.clewa.card.exception;

import org.calik.clewa.common.exception.ClewaException;

public class CardConflictException extends ClewaException {

	public CardConflictException(String message) {
		super("CARD_CONFLICT", message);
	}

}
