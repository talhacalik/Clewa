package org.calik.clewa.wallet.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Bir Transfer kaydı, oluşturulduktan sonra hiç değişmemeli (finansal audit trail — bkz. CLAUDE.md
// §9.2). Bu yüzden hiç setter yok (Lombok @Setter hiç eklenmedi), sadece constructor. Aynı gerekçeyle
// updatedAt de BİLİNÇLİ OLARAK YOK (diğer entity'lerdeki createdAt/updatedAt standardından sapma):
// bu satır hiçbir zaman tekrar save()/merge() edilmeyeceği için updatedAt her zaman createdAt'e eşit
// kalırdı — işlevsiz bir alan olurdu (java-reviewer, Faz 3 incelemesi).
// status alanı bilinçli olarak eklenmedi: TransferService (Faz 4) bakiye kontrolü + düşme + ekleme +
// bu kaydı TEK bir @Transactional metotta yapacak; herhangi bir adım başarısız olursa tüm transaction
// rollback olur, satır hiç yazılmaz. Yani var olan her Transfer, tanımı gereği zaten tamamlanmıştır.
@Entity
@Table(name = "transfers",
		// Tekillik BİLİNÇLİ OLARAK sadece idempotency_key üzerinde DEĞİL, (sender_wallet_id,
		// idempotency_key) ikilisi üzerinde kuruluyor: aksi halde iki farklı kullanıcı aynı key
		// değerini kullanırsa (zayıf/deterministik client key üretimi), biri diğerinin transfer
		// kaydını (tutar, alıcı, tarih) hiçbir yetki kontrolünden geçmeden görebilirdi
		// (security-reviewer, Faz 4 incelemesi — CRITICAL).
		uniqueConstraints = @UniqueConstraint(name = "uk_transfers_sender_wallet_idempotency_key",
				columnNames = { "sender_wallet_id", "idempotency_key" }),
		indexes = {
				@Index(name = "idx_transfers_sender_wallet_id", columnList = "sender_wallet_id"),
				@Index(name = "idx_transfers_receiver_wallet_id", columnList = "receiver_wallet_id") })
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_wallet_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_transfers_sender_wallet_id"))
	private Wallet senderWallet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "receiver_wallet_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_transfers_receiver_wallet_id"))
	private Wallet receiverWallet;

	@Check(name = "chk_transfers_amount_positive", constraints = "amount > 0")
	@Column(nullable = false, updatable = false, columnDefinition = "numeric(19,2)")
	private BigDecimal amount;

	// Client-üretimi bir tekrar-deneme anahtarı (bkz. CLAUDE.md §3.7); 100, tipik bir UUID'den
	// (36 karakter) belirgin şekilde geniş bir pay bırakıyor.
	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
	private String idempotencyKey;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public Transfer(Wallet senderWallet, Wallet receiverWallet, BigDecimal amount, String idempotencyKey) {
		this.senderWallet = senderWallet;
		this.receiverWallet = receiverWallet;
		// Wallet.setBalance ile aynı gerekçe (bkz. CLAUDE.md §3.5): ölçek burada normalize edilmezse,
		// ileride TransferService bu değeri bir aritmetik işlem sonucundan üretirse scale tutarsızlığı
		// sessizce oluşabilir.
		this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
		this.idempotencyKey = idempotencyKey;
	}

}
