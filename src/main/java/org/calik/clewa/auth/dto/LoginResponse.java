package org.calik.clewa.auth.dto;

import org.calik.clewa.auth.entity.User;

public record LoginResponse(Long id, String phoneNumber, String firstName, String lastName) {

	public static LoginResponse from(User user) {
		return new LoginResponse(user.getId(), user.getPhoneNumber(), user.getFirstName(), user.getLastName());
	}

}
