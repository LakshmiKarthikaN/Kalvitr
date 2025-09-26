package com.kalvitrack_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long userId;
    private String fullName;
    private String email;
    private String role;
    private String status;
    private Boolean mustResetPassword;
    private Integer failedLoginAttempts;
    private String createdAt;
    private String updatedAt;
}
