package com.kalvitrack_backend.dto.emailverifyfeature;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationResponseDto {

    private boolean exists;
    private String role;
    private boolean registrationComplete;
    private String message;
    public static EmailVerificationResponseDto exists(String role, boolean isComplete) {
        return new EmailVerificationResponseDto(
                true,
                role,
                isComplete,
                isComplete ? "Registration already completed" : "Email verified. Please complete registration."
        );
    }

    public static EmailVerificationResponseDto notExists() {
        return new EmailVerificationResponseDto(
                false,
                null,
                false,
                "Email not found in our system"
        );
    }
}