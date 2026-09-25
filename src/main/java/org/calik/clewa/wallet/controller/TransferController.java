package org.calik.clewa.wallet.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.calik.clewa.common.response.ApiResponse;
import org.calik.clewa.wallet.dto.TransferRequest;
import org.calik.clewa.wallet.dto.TransferResponse;
import org.calik.clewa.wallet.entity.Transfer;
import org.calik.clewa.wallet.service.TransferService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

	private final TransferService transferService;

	@PostMapping
	public ResponseEntity<ApiResponse<TransferResponse>> transfer(
			@Valid @RequestBody TransferRequest request,
			@RequestHeader("Idempotency-Key")
			@NotBlank(message = "{wallet.idempotencyKey.required}")
			@Size(max = Transfer.IDEMPOTENCY_KEY_MAX_LENGTH, message = "{wallet.idempotencyKey.size}")
			String idempotencyKey,
			Authentication authentication) {
		// Gönderen SADECE oturumdan (authentication) alınır; TransferRequest'te gönderen alanı yok ve
		// istek gövdesindeki hiçbir değer buraya taşınmaz. Aksi halde biri başkası adına para gönderebilirdi.
		Transfer transfer = transferService.transfer(authentication.getName(), request.recipientAccountNumber(),
				request.amount(), idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(TransferResponse.from(transfer)));
	}

}
