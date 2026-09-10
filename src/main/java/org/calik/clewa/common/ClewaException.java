package org.calik.clewa.common;

public abstract class ClewaException extends RuntimeException {

	private final String errorCode;

	protected ClewaException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return errorCode;
	}

}
