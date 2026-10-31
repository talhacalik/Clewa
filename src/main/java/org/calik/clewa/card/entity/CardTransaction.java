package org.calik.clewa.card.entity;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Bir kart harcaması kaydı; oluşturulduktan sonra hiç değişmez (finansal audit trail, bkz. Transfer ile aynı gerekçe):
// setter yok, updatedAt yok, status yok (var olan her kayıt tamamlanmış bir harcamadır; hata olursa transaction
// rollback olur ve satır hiç yazılmaz). Idempotency anahtarı kart başına tekildir, tek başına global değil
// (bkz. Transfer.java'daki uk_transfers_sender_wallet_idempotency_key gerekçesi).
@Entity
@Table(name = "card_transactions",
		// card_id için ayrıca index YOK: (card_id, idempotency_key) birleşik tekil index'i card_id aramasını zaten
		// karşılar (database-reviewer). Harcama geçmişi listesi eklenirse (card_id, created_at DESC) index'i orada eklenir.
		uniqueConstraints = @UniqueConstraint(name = "uk_card_transactions_card_idempotency_key",
				columnNames = { "card_id", "idempotency_key" }))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class CardTransaction {

	public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;
	public static final int MERCHANT_NAME_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_card_transactions_card_id"))
	private Card card;

	@Check(name = "chk_card_transactions_amount_positive", constraints = "amount > 0")
	@Column(nullable = false, updatable = false, columnDefinition = "numeric(19,2)")
	private BigDecimal amount;

	@Check(name = "chk_card_transactions_merchant_name_not_blank", constraints = "length(btrim(merchant_name)) > 0")
	@Column(name = "merchant_name", nullable = false, updatable = false, length = MERCHANT_NAME_MAX_LENGTH)
	private String merchantName;

	@Column(name = "idempotency_key", nullable = false, updatable = false, length = IDEMPOTENCY_KEY_MAX_LENGTH)
	private String idempotencyKey;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public CardTransaction(Card card, BigDecimal amount, String merchantName, String idempotencyKey) {
		this.card = card;
		// Wallet.setBalance / Transfer ile aynı gerekçe (CLAUDE.md §3.5): ölçek her zaman 2.
		this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
		this.merchantName = merchantName;
		this.idempotencyKey = idempotencyKey;
	}

}
