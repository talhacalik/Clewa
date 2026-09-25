package org.calik.clewa.card.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import org.calik.clewa.wallet.entity.Wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Sadece @Getter var, hiç setter yok: durum/limit değişiklikleri, ihtiyaç doğduğunda (Faz 3, Faz 5)
// freeze()/changeDailyLimit()/spend() gibi iş kurallarını da içeren domain metotlarıyla yapılacak;
// dışarıdan alanları doğrudan değiştirmeyi baştan kapatıyoruz (Wallet.credit/debit ile aynı gerekçe).
@Entity
@Table(name = "cards", uniqueConstraints = {
		@UniqueConstraint(name = "uk_cards_wallet_id", columnNames = "wallet_id"),
		@UniqueConstraint(name = "uk_cards_card_number", columnNames = "card_number") })
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class Card {

	public static final int CARD_NUMBER_LENGTH = 16;

	public static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("500000.00");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// unique (uk_cards_wallet_id): bir cüzdanın en fazla bir kartı olabilir ("kullanıcı başına tek kart" kararı).
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "wallet_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_cards_wallet_id"))
	private Wallet wallet;

	// Format kısıtı, CardNumberGenerator'da (Faz 2) ileride çıkabilecek bir hataya karşı ikinci savunma katmanı.
	@Check(name = "chk_cards_card_number_format", constraints = "card_number ~ '^[0-9]{16}$'")
	@Column(name = "card_number", nullable = false, updatable = false, length = CARD_NUMBER_LENGTH)
	private String cardNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CardStatus status = CardStatus.ACTIVE;

	// Kullanıcının istediği günlük limit; değişiklik ertesi gün geçerli olur (gece yarısı görevi
	// remainingDailyLimit'i buna eşitler). Bkz. CLAUDE.md §7.4.
	@Check(name = "chk_cards_daily_limit_positive", constraints = "daily_limit > 0")
	@Column(name = "daily_limit", nullable = false, columnDefinition = "numeric(19,2)")
	private BigDecimal dailyLimit;

	// Bugün kalan limit; harcamada bundan düşülür, negatif olamaz.
	@Check(name = "chk_cards_remaining_daily_limit_non_negative", constraints = "remaining_daily_limit >= 0")
	@Column(name = "remaining_daily_limit", nullable = false, columnDefinition = "numeric(19,2)")
	private BigDecimal remainingDailyLimit;

	// Dikkat (Faz 7, gece yarısı sıfırlama): toplu bir "update Card set remainingDailyLimit = dailyLimit"
	// sorgusu version'ı artırmaz; o sırada okunmuş bir kartla yapılan bir harcama, sıfırlamanın üstüne
	// yazabilir. Sıfırlama sorgusu "update versioned" ile ya da elle "version = version + 1" ekleyerek
	// yazılmalı (database-reviewer, Adım 4 Faz 1 incelemesi).
	@Version
	@Column(nullable = false)
	private Long version;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	public Card(Wallet wallet, String cardNumber) {
		this.wallet = wallet;
		this.cardNumber = cardNumber;
		this.dailyLimit = DEFAULT_DAILY_LIMIT;
		this.remainingDailyLimit = DEFAULT_DAILY_LIMIT;
	}

}
