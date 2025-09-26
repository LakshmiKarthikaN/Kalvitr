package com.kalvitrack_backend.dto.emailverifyfeature;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationDto {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
}