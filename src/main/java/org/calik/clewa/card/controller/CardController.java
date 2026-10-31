package org.calik.clewa.card.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.calik.clewa.card.dto.CardResponse;
import org.calik.clewa.card.dto.SpendRequest;
import org.calik.clewa.card.dto.SpendResponse;
import org.calik.clewa.card.dto.UpdateDailyLimitRequest;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.entity.CardTransaction;
import org.calik.clewa.card.service.CardService;
import org.calik.clewa.card.service.CardSpendingService;
import org.calik.clewa.common.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/card")
@RequiredArgsConstructor
public class CardController {

	private final CardService cardService;
	private final CardSpendingService cardSpendingService;

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

	// Kart ve gönderen SADECE oturumdan (Authentication) bulunur; istekte kart no/kullanıcı alanı yok.
	@PostMapping("/spend")
	public ResponseEntity<ApiResponse<SpendResponse>> spend(
			@Valid @RequestBody SpendRequest request,
			@RequestHeader("Idempotency-Key")
			@NotBlank(message = "{wallet.idempotencyKey.required}")
			@Size(max = CardTransaction.IDEMPOTENCY_KEY_MAX_LENGTH, message = "{wallet.idempotencyKey.size}")
			String idempotencyKey,
			Authentication authentication) {
		CardTransaction transaction = cardSpendingService.spend(authentication.getName(), request.amount(),
				request.merchantName(), idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(SpendResponse.from(transaction)));
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
