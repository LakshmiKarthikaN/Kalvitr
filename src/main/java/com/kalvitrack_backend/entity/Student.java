package com.kalvitrack_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.Year;

@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_id")
    private Long id;

    // Basic Info
    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "email", length = 255, unique = true, nullable = false)
    private String email;

    @Column(name = "hashed_password", length = 255)
    private String hashedPassword;

    @Column(name = "mobile_number", length = 20)
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be exactly 10 digits")

    private String mobileNumber;

    @Column(name = "college_name", length = 200)
    private String collegeName;

    @Column(name = "year_of_graduation")
    private Year yearOfGraduation;

    @Column(name = "resume_path", length = 500)
    private String resumePath;

    // Role Management
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private StudentRole role;

    // Account Security
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    @Column(name = "reset_password_token", length = 255)
    private String resetPasswordToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    // Security Tracking
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_failed_attempt")
    private LocalDateTime lastFailedAttempt;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    // Timestamps
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // Enums
    public enum StudentRole {
        ZSGS, PMIS
    }

    public enum StudentStatus {
        PENDING, ACTIVE, INACTIVE, SUSPENDED
    }
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (emailVerified == null) {
            emailVerified = false;
        }
        if (failedLoginAttempts == null) {
            failedLoginAttempts = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    public void setRegistrationComplete(boolean complete) {}

    // Helper methods
    public boolean isRegistrationComplete() {
        return this.fullName != null && !this.fullName.trim().isEmpty() &&
                this.hashedPassword != null && !this.hashedPassword.trim().isEmpty() &&
                this.mobileNumber != null && !this.mobileNumber.trim().isEmpty() &&
                this.collegeName != null && !this.collegeName.trim().isEmpty() &&
                this.yearOfGraduation != null &&
                this.emailVerified != null && this.emailVerified;
    }


    public boolean isAccountLocked() {
        return accountLockedUntil != null && accountLockedUntil.isAfter(LocalDateTime.now());
    }
}