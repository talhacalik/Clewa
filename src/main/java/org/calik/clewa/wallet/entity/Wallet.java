package org.calik.clewa.wallet.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import org.calik.clewa.auth.entity.User;
import org.calik.clewa.wallet.exception.InsufficientBalanceException;
import org.calik.clewa.wallet.util.AccountNumberGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallets", uniqueConstraints = {
		@UniqueConstraint(name = "uk_wallets_user_id", columnNames = "user_id"),
		@UniqueConstraint(name = "uk_wallets_account_number", columnNames = "account_number") })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_wallets_user_id"))
	@Setter(AccessLevel.NONE)
	private User user;

	// Sunucu tarafında üretilir (bkz. AccountNumberGenerator), kullanıcıdan asla doğrudan alınmaz.
	// Yine de phoneNumber'daki gibi bir CHECK kısıtı ekliyoruz: bu, kötü niyetli girdiye karşı değil,
	// AccountNumberGenerator'da ileride çıkabilecek bir hataya (ya da generator'ı bypass eden bir
	// migration/admin aracına) karşı ikinci bir savunma katmanı (defense in depth) — bkz. CLAUDE.md §3.10.
	@Check(name = "chk_wallets_account_number_format", constraints = "account_number ~ '^[0-9]{10}$'")
	@Column(name = "account_number", nullable = false, updatable = false,
			length = AccountNumberGenerator.ACCOUNT_NUMBER_LENGTH)
	@Setter(AccessLevel.NONE)
	private String accountNumber;

	@Check(name = "chk_wallets_balance_non_negative", constraints = "balance >= 0")
	@Column(nullable = false, columnDefinition = "numeric(19,2) default 0.00")
	@Setter(AccessLevel.NONE)
	private BigDecimal balance = new BigDecimal("0.00");

	@Version
	@Column(nullable = false)
	@Setter(AccessLevel.NONE)
	private Long version;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	public Wallet(User user, String accountNumber) {
		this.user = user;
		this.accountNumber = accountNumber;
	}

	// Para tutarları her zaman kuruş hassasiyetinde (2 ondalık basamak) tutulur — bkz. CLAUDE.md §3.5.
	// Bu setter, servis katmanında hesaplanan bir bakiyenin ölçeği kontrolsüz kalırsa bile burada
	// normalize edilmesini garanti eder. İş mantığı (transfer, ödeme) bu setter'ı doğrudan
	// çağırmak yerine credit()/debit() kullanmalı — bunlar public kalıyor çünkü
	// WalletRepositoryTest'teki bazı testler DB CHECK kısıtlarını izole doğrulamak için
	// bilerek geçersiz bir bakiyeyi buradan set ediyor (bkz. CLAUDE.md §7.3 Faz 2 notu).
	public void setBalance(BigDecimal balance) {
		Objects.requireNonNull(balance, "balance null olamaz");
		this.balance = balance.setScale(2, RoundingMode.HALF_EVEN);
	}

	public void credit(BigDecimal amount) {
		validatePositiveAmount(amount);
		setBalance(this.balance.add(amount));
	}

	public void debit(BigDecimal amount) {
		validatePositiveAmount(amount);
		BigDecimal newBalance = this.balance.subtract(amount);
		if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
			throw new InsufficientBalanceException("Yetersiz bakiye.");
		}
		setBalance(newBalance);
	}

	private void validatePositiveAmount(BigDecimal amount) {
		Objects.requireNonNull(amount, "amount null olamaz");
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Tutar sıfırdan büyük olmalı.");
		}
	}

}
