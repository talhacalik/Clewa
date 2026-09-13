package org.calik.clewa.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// Giriş başarılı olunca, kimliği oturuma (HTTP session) kaydetmek için AuthController bu bean'i
	// kullanır. Aynı bean'i aşağıdaki filtre zincirine de veriyoruz ki sonraki isteklerde de
	// oturumdaki kimlik okunabilsin.
	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
			throws Exception {
		// Bu üç adres, kullanıcının henüz bir oturumu olmadan (kayıt olurken/giriş yaparken/doğrularken)
		// çağırdığı adresler; bu yüzden hem girişsiz erişime hem de CSRF muafiyetine açıkça (tek tek)
		// izin veriliyor. Genel kural hâlâ geçerli (bkz. CLAUDE.md 13): CSRF, JWT'ye geçilene kadar
		// başka her yerde açık kalır.
		String[] anonymousAuthEndpoints = { "/api/auth/signup", "/api/auth/verify-phone", "/api/auth/login" };

		http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(anonymousAuthEndpoints).permitAll()
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.ignoringRequestMatchers(anonymousAuthEndpoints))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			// Varsayılan (formLogin/httpBasic hiç açılmadığı için) Spring Security kimliksiz istekleri
			// 403 ile reddediyor; CLAUDE.md §9.3 sözleşmesi "kimlik doğrulama yok/geçersiz" için 401
			// istediğinden bunu açıkça 401'e sabitliyoruz.
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

}
