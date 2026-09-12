package org.calik.clewa.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

	@NotBlank(message = "{signup.phoneNumber.required}")
	String phoneNumber,

	@NotBlank(message = "{login.password.required}")
	String password

) {
}
