package org.calik.clewa.card.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.calik.clewa.card.dto.CardResponse;
import org.calik.clewa.card.entity.Card;
import org.calik.clewa.card.service.CardService;
import org.calik.clewa.common.response.ApiResponse;

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

}
