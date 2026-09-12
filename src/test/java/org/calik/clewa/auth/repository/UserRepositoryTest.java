package org.calik.clewa.auth.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import org.calik.clewa.AbstractIntegrationTest;
import org.calik.clewa.auth.entity.AccountStatus;
import org.calik.clewa.auth.entity.User;
import org.calik.clewa.common.config.JpaAuditingConfig;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class UserRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void findByPhoneNumber_shouldReturnSavedUserWithDefaults() {
		User user = new User();
		user.setPhoneNumber("+905321234567");
		user.setPasswordHash("hashed-password");
		user.setFirstName("Ahmet");
		user.setLastName("Yılmaz");
		user.setDateOfBirth(LocalDate.of(1995, 3, 20));

		userRepository.save(user);

		var found = userRepository.findByPhoneNumber("+905321234567");

		assertThat(found).isPresent();
		assertThat(found.get().getFirstName()).isEqualTo("Ahmet");
		assertThat(found.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(found.get().isPhoneVerified()).isFalse();
		assertThat(found.get().getCreatedAt()).isNotNull();
		assertThat(found.get().getUpdatedAt()).isNotNull();
	}

}
