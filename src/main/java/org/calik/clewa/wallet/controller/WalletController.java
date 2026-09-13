package org.calik.clewa.wallet.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.calik.clewa.common.response.ApiResponse;
import org.calik.clewa.wallet.dto.WalletResponse;
import org.calik.clewa.wallet.entity.Wallet;
import org.calik.clewa.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

	private final WalletService walletService;

	@GetMapping
	public ResponseEntity<ApiResponse<WalletResponse>> getWallet(Authentication authentication) {
		Wallet wallet = walletService.getWalletForCurrentUser(authentication.getName());
		return ResponseEntity.ok(ApiResponse.success(WalletResponse.from(wallet)));
	}

}
