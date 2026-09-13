package org.calik.clewa.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.calik.clewa.wallet.entity.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

	Optional<Wallet> findByUserId(Long userId);

	Optional<Wallet> findByUser_PhoneNumber(String phoneNumber);

}
