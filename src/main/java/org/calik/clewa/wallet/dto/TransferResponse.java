package org.calik.clewa.wallet.dto;

import org.calik.clewa.wallet.entity.Transfer;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(String recipientAccountNumber, BigDecimal amount, Instant createdAt){

    public static TransferResponse from(Transfer transfer){
        return new TransferResponse(transfer.getReceiverWallet().getAccountNumber(), transfer.getAmount(), transfer.getCreatedAt());
    }
}
