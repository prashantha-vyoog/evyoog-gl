package com.evyoog.gl.common.response;

import java.util.List;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        List<FieldError> errors
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<?> error(String message, List<FieldError> errors) {
        return new ApiResponse<>(false, null, message, errors);
    }
}
