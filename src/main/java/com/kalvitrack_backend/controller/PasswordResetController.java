package com.kalvitrack_backend.controller;



import com.kalvitrack_backend.dto.*;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "https://kalvitrack.vercel.app")

public class PasswordResetController {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetController.class);

    @Autowired
    private PasswordResetService passwordResetService;

    /**
     * Forgot Password - Send reset link (Not available for Admin users)
     * Rate limited and IP tracked
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIpAddress(httpRequest);
        passwordResetService.initiatePasswordReset(request.getEmail(), clientIp);

        return ResponseEntity.ok(new ApiResponse(true, "Reset password link has been sent to your email address"));
    }



    /**
     * Reset Password using token (Works for both forgot password and force reset flows)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIpAddress(httpRequest);
        logger.info("Password reset attempt with token from IP: {}", clientIp);

        try {
            String message = passwordResetService.resetPassword(
                    request.getToken(), request.getNewPassword(), clientIp);

            logger.info("Password reset completed successfully");
            return ResponseEntity.ok(new ApiResponse(true, message));

        } catch (RuntimeException e) {
            logger.warn("Password reset failed with token - {}", e.getMessage());

            HttpStatus status = determineErrorStatus(e.getMessage());
            return ResponseEntity.status(status)
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Force Password Reset (for users with temporary passwords or mandatory resets)
     * This generates a token and can reuse the reset-password endpoint
     */
    @PostMapping("/force-reset-password")
    @PreAuthorize("hasAnyRole('HR', 'FACULTY','INTERVIEW_PANELIST')")
    public ResponseEntity<ApiResponse> forceResetPassword(
            @Valid @RequestBody ForcedResetRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIpAddress(httpRequest);
        logger.info("Force password reset requested for user ID: {} from IP: {}",
                request.getUserId(), clientIp);

        try {
            // Validate current password and generate reset token
            String resetToken = passwordResetService.initiateForceReset(
                    request.getUserId(), request.getCurrentPassword(), clientIp);

            // Now reset with new password using the same flow
            String message = passwordResetService.resetPassword(
                    resetToken, request.getNewPassword(), clientIp);

            logger.info("Force password reset completed for user ID: {}", request.getUserId());
            return ResponseEntity.ok(new ApiResponse(true, message));

        } catch (RuntimeException e) {
            logger.warn("Force password reset failed for user ID: {} - {}",
                    request.getUserId(), e.getMessage());

            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Validate reset token (for frontend validation)
     */
    @GetMapping("/validate-reset-token")
    public ResponseEntity<ValidateTokenResponse> validateResetToken(
            @RequestParam String token,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIpAddress(httpRequest);
        logger.info("Token validation requested from IP: {}", clientIp);

        try {
            ValidateTokenResponse response = passwordResetService.validateResetToken(token);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.warn("Token validation failed - {}", e.getMessage());

            return ResponseEntity.badRequest()
                    .body(new ValidateTokenResponse(false, e.getMessage(), null));
        }
    }

    /**
     * Check if user needs password reset (for frontend routing)
     */
    @GetMapping("/check-password-reset-required")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> checkPasswordResetRequired(@RequestParam Long userId) {
        try {
            User user = passwordResetService.getUserById(userId);
            boolean mustReset = passwordResetService.mustResetPassword(user);
            return ResponseEntity.ok(
                    new ApiResponse(true, mustReset ? "Password reset required" : "No reset required"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Admin endpoint to force password reset for a user
     */
    @PostMapping("/admin/force-user-reset/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> adminForceUserReset(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIpAddress(httpRequest);
        logger.info("Admin forcing password reset for user ID: {} from IP: {}", userId, clientIp);

        try {
            String message = passwordResetService.adminForcePasswordReset(userId, clientIp);
            logger.info("Admin successfully forced password reset for user ID: {}", userId);
            return ResponseEntity.ok(new ApiResponse(true, message));

        } catch (RuntimeException e) {
            logger.warn("Admin force password reset failed for user ID: {} - {}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // Helper methods
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null || xForwardedForHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xForwardedForHeader.split(",")[0].trim();
    }

    private HttpStatus determineErrorStatus(String errorMessage) {
        if (errorMessage.contains("not found") || errorMessage.contains("Invalid or expired")) {
            return HttpStatus.NOT_FOUND;
        } else if (errorMessage.contains("Admin accounts cannot") || errorMessage.contains("Access denied")) {
            return HttpStatus.FORBIDDEN;
        } else if (errorMessage.contains("Too many") || errorMessage.contains("rate limit")) {
            return HttpStatus.TOO_MANY_REQUESTS;
        } else if (errorMessage.contains("Account is locked") || errorMessage.contains("temporarily locked")) {
            return HttpStatus.LOCKED;
        }
        return HttpStatus.BAD_REQUEST;
    }
}