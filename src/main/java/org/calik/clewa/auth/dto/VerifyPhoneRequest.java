package org.calik.clewa.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyPhoneRequest(

	@NotBlank(message = "{signup.phoneNumber.required}")
	String phoneNumber,

	@NotBlank(message = "{verifyPhone.code.required}")
	@Pattern(regexp = "\\d{6}", message = "{verifyPhone.code.invalid}")
	String code

) {
}
