package org.calik.clewa.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.calik.clewa.wallet.entity.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

	Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

}
