package org.calik.clewa.common.response;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, ErrorDetail error, Instant timestamp) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null, Instant.now());
	}

	public static <T> ApiResponse<T> error(String code, String message) {
		return new ApiResponse<>(false, null, new ErrorDetail(code, message), Instant.now());
	}

}
