package org.calik.clewa.card.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.calik.clewa.card.entity.Card;
import org.calik.clewa.wallet.entity.Wallet;

public interface CardRepository extends JpaRepository<Card, Long> {

	Optional<Card> findByWallet_User_PhoneNumber(String phoneNumber);

	boolean existsByWallet(Wallet wallet);

	boolean existsByCardNumber(String cardNumber);

}
