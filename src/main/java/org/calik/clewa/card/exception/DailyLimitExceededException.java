package org.calik.clewa.card.exception;

import org.calik.clewa.common.exception.ClewaException;

public class DailyLimitExceededException extends ClewaException {

	public DailyLimitExceededException(String message) {
		super("DAILY_LIMIT_EXCEEDED", message);
	}

}
