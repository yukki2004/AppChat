package com.chatapp.core.base;

/**
 * Standard envelope for every REST response of Core Service (except `/health*` — an
 * infra endpoint for K8s probes, not a business API, kept in its own minimal format).
 * `success` is a separate field rather than inferred from `data == null`, since some
 * valid endpoints legitimately return `data = null` (e.g. logout) — that must not read as
 * an error.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message));
    }
}
