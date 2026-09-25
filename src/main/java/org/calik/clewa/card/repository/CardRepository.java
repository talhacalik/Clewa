package org.calik.clewa.card.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.calik.clewa.card.entity.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

}
