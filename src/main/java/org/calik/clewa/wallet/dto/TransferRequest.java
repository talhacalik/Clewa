package org.calik.clewa.wallet.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.calik.clewa.wallet.util.AccountNumberGenerator;

import java.math.BigDecimal;

public record TransferRequest (

        @NotBlank(message = "{wallet.accountNumber.required}")
        @Pattern(regexp = "^[0-9]{" + AccountNumberGenerator.ACCOUNT_NUMBER_LENGTH + "}$",
                message = "{wallet.accountNumber.invalid}")
        String recipientAccountNumber,

        // integer = 17, fraction = 2: veritabanındaki NUMERIC(19,2) sütunuyla birebir uyumlu. Bu sınır olmazsa
        // 0.015 gibi kuruş altı tutarlar gönderen/alıcı/kayıt tarafında ayrı ayrı yuvarlanıp 1 kuruş yoktan
        // var edebiliyor; 1e10000000 gibi dev üslü sayılar da gereksiz hesaplama yükü yaratıyor.
        @NotNull(message = "{wallet.amount.required}")
        @Positive(message = "{wallet.amount.positive}")
        @Digits(integer = 17, fraction = 2, message = "{wallet.amount.digits}")
        BigDecimal amount
) {
}
