package org.calik.clewa.card.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.calik.clewa.card.entity.Card;
import org.calik.clewa.wallet.entity.Wallet;

public interface CardRepository extends JpaRepository<Card, Long> {

	// Servis kartın cüzdanına ve kullanıcısına (hesap durumu) erişiyor; bunları tek sorguda getirerek ek lazy sorguları önler.
	@EntityGraph(attributePaths = { "wallet", "wallet.user" })
	Optional<Card> findByWallet_User_PhoneNumber(String phoneNumber);

	boolean existsByWallet(Wallet wallet);

	boolean existsByCardNumber(String cardNumber);

}
