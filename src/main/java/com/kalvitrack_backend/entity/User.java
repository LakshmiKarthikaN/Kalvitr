package com.kalvitrack_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_roles")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", unique = true, nullable = false)
    private String email;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "hashed_password", nullable = false)
    private String hashedPassword;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status = Status.ACTIVE;

    @Column(name = "must_reset_password")
    private Boolean mustResetPassword = false;

    @Column(name = "reset_password_token")
    private String resetPasswordToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_failed_attempt")
    private LocalDateTime lastFailedAttempt;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "reset_attempts_count")
    private Integer resetAttemptsCount = 0;

    @Column(name = "last_reset_attempt")
    private LocalDateTime lastResetAttempt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    public enum Role { ADMIN, HR, FACULTY, INTERVIEW_PANELIST }
    public enum Status { ACTIVE, INACTIVE, SUSPENDED }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isAccountLocked() {
        return accountLockedUntil != null && accountLockedUntil.isAfter(LocalDateTime.now());
    }
    public boolean isActive() {
        return status == Status.ACTIVE && !isAccountLocked();
    }
    public boolean canUsePasswordReset() {
        return role != Role.ADMIN;
    }

    public void incrementFailedAttempts() {
        failedLoginAttempts = failedLoginAttempts == null ? 1 : failedLoginAttempts + 1;
        lastFailedAttempt = LocalDateTime.now();
    }

    public void resetFailedAttempts() {
        failedLoginAttempts = 0;
        lastFailedAttempt = null;
        accountLockedUntil = null;
    }

    public void lockAccount(int minutes) {
        accountLockedUntil = LocalDateTime.now().plusMinutes(minutes);
    }

    public void incrementResetAttempts() {
        resetAttemptsCount = resetAttemptsCount == null ? 1 : resetAttemptsCount + 1;
        lastResetAttempt = LocalDateTime.now();
    }

    public void markPasswordChanged() {
        passwordChangedAt = LocalDateTime.now();
        mustResetPassword = false;
    }
}
