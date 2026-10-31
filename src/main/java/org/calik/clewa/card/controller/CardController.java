package org.calik.clewa.card.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.calik.clewa.card.dto.CardResponse;
import org.calik.clewa.card.dto.UpdateDailyLimitRequest;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.service.CardService;
import org.calik.clewa.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/card")
@RequiredArgsConstructor
public class CardController {

	private final CardService cardService;

	// İki endpoint de kartı SADECE oturumdaki kullanıcıdan (Authentication) bulur; istekten kart no/id alınmaz.
	@PostMapping
	public ResponseEntity<ApiResponse<CardResponse>> createCard(Authentication authentication) {
		Card card = cardService.createCard(authentication.getName());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(CardResponse.from(card)));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<CardResponse>> getCard(Authentication authentication) {
		Card card = cardService.getCardForCurrentUser(authentication.getName());
		return ResponseEntity.ok(ApiResponse.success(CardResponse.from(card)));
	}

	@PostMapping("/freeze")
	public ResponseEntity<ApiResponse<CardResponse>> freezeCard(Authentication authentication) {
		Card card = cardService.freezeCard(authentication.getName());
		return ResponseEntity.ok(ApiResponse.success(CardResponse.from(card)));
	}

	@PostMapping("/unfreeze")
	public ResponseEntity<ApiResponse<CardResponse>> unfreezeCard(Authentication authentication) {
		Card card = cardService.unfreezeCard(authentication.getName());
		return ResponseEntity.ok(ApiResponse.success(CardResponse.from(card)));
	}

	// Yeni limit ertesi gün geçerli olur; cevaptaki dailyLimit (yeni) ve remainingDailyLimit (bugün kalan, değişmez)
	// farkını göstermek arayüzün işi (bkz. CLAUDE.md §7.4).
	@PutMapping("/daily-limit")
	public ResponseEntity<ApiResponse<CardResponse>> changeDailyLimit(
			@Valid @RequestBody UpdateDailyLimitRequest request, Authentication authentication) {
		Card card = cardService.changeDailyLimit(authentication.getName(), request.dailyLimit());
		return ResponseEntity.ok(ApiResponse.success(CardResponse.from(card)));
	}

}
