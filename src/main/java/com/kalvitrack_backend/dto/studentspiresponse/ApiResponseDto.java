package com.kalvitrack_backend.dto.studentspiresponse;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ApiResponseDto<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponseDto<T> success(String message, T data) {
        return new ApiResponseDto<>(true, message, data);
    }

    public static <T> ApiResponseDto<T> error(String message) {
        return new ApiResponseDto<>(false, message, null);
    }
}