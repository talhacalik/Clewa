package org.calik.clewa.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.calik.clewa.auth.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByPhoneNumber(String phoneNumber);

}
