package com.kalvitrack_backend.dto;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder // Add this annotation to enable builder pattern
public class AdminLoginResponse {
    private String token;
    private String message;
    private boolean success;
    private String role;
    private Long userId;
    private String email;
    private String status;
    private boolean mustResetPassword;
    private String fullName;
    private String resetToken; // Add this field since StudentService is trying to set it
    private String redirectTo;
    // Constructor for failed login
    public AdminLoginResponse(String message, boolean success) {
        this.message = message;
        this.success = success;
        this.token = null;
        this.role = null;
        this.userId = null;
        this.email = null;
        this.status = null;
        this.mustResetPassword = false;
        this.fullName = null;
    }

    // Constructor for successful login
    public AdminLoginResponse(String token, String message, String role, Long userId, String email, String status) {
        this.token = token;
        this.message = message;
        this.success = true;
        this.role = role;
        this.userId = userId;
        this.email = email;
        this.status = status;
        this.mustResetPassword = false;
        this.fullName = null;
        this.resetToken = null;
    }
}