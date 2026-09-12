package org.calik.clewa.auth.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

	@NotBlank(message = "{signup.phoneNumber.required}")
	String phoneNumber,

	@NotBlank(message = "{signup.password.required}")
	@Size(max = 72, message = "{signup.password.size}")
	@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "{signup.password.invalid}")
	String password,

	@NotBlank(message = "{signup.firstName.required}")
	@Size(min = 2, max = 50, message = "{signup.firstName.size}")
	String firstName,

	@NotBlank(message = "{signup.lastName.required}")
	@Size(min = 2, max = 50, message = "{signup.lastName.size}")
	String lastName,

	@NotNull(message = "{signup.dateOfBirth.required}")
	@Past(message = "{signup.dateOfBirth.past}")
	LocalDate dateOfBirth

) {
}
