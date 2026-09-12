package org.calik.clewa.auth.dto;

import org.calik.clewa.auth.entity.User;

public record SignupResponse(Long id, String phoneNumber, String firstName, String lastName, boolean phoneVerified) {

	public static SignupResponse from(User user) {
		return new SignupResponse(user.getId(), user.getPhoneNumber(), user.getFirstName(), user.getLastName(), user.isPhoneVerified());
	}

}
