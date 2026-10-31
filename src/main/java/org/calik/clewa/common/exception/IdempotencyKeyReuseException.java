package org.calik.clewa.common.exception;

// Aynı Idempotency-Key, daha önce işlenmiş bir isteğinkinden FARKLI bir içerikle (tutar, alıcı, işyeri) tekrar kullanıldı.
// Sessizce eski sonucu dönmek, istemciye yeni işlemin yapıldığını sandırırdı; bu yüzden açıkça reddedilir.
public class IdempotencyKeyReuseException extends ClewaException {

	public IdempotencyKeyReuseException(String message) {
		super("IDEMPOTENCY_KEY_REUSED", message);
	}

}
