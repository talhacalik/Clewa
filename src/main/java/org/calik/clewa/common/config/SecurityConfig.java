package org.calik.clewa.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// Bu iki adres, kullanıcının henüz bir oturumu olmadan (kayıt olurken/doğrularken) çağırdığı
		// adresler; bu yüzden hem girişsiz erişime hem de CSRF muafiyetine açıkça (tek tek) izin veriliyor.
		// Genel kural hâlâ geçerli (bkz. CLAUDE.md 13): CSRF, JWT'ye geçilene kadar başka her yerde açık kalır.
		String[] anonymousAuthEndpoints = { "/api/auth/signup", "/api/auth/verify-phone" };

		http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(anonymousAuthEndpoints).permitAll()
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.ignoringRequestMatchers(anonymousAuthEndpoints));
		return http.build();
	}

}
