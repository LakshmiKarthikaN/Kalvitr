package com.kalvitrack_backend.service.impl;

import com.kalvitrack_backend.config.jwthandler.JwtUtil;
import com.kalvitrack_backend.dto.AdminLoginRequest;
import com.kalvitrack_backend.dto.AdminLoginResponse;
import com.kalvitrack_backend.entity.Student;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.repository.StudentRepository;
import com.kalvitrack_backend.repository.UserRepository;
import com.kalvitrack_backend.service.AdminService;
import com.kalvitrack_backend.service.EmailService;
import com.kalvitrack_backend.service.PasswordResetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class AdminServiceImpl implements AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordResetService passwordResetService;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    @Override
    public User createAdmin(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Admin with email already exists");
        }

        user.setHashedPassword(passwordEncoder.encode(user.getHashedPassword()));
        user.setRole(User.Role.ADMIN);
        user.setStatus(User.Status.ACTIVE);
        user.setMustResetPassword(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        return loginAsUser(request);
    }

    @Override
    public AdminLoginResponse loginByRole(AdminLoginRequest request, User.Role expectedRole) {
        try {
            Optional<User> userOpt = userRepository.findByEmailAndStatus(request.getEmail(), User.Status.ACTIVE);

            if (userOpt.isEmpty()) {
                return new AdminLoginResponse("Invalid email or password", false);
            }

            User user = userOpt.get();

            // Check if account is locked
            if (isAccountLocked(user)) {
                return new AdminLoginResponse(
                        "Account is temporarily locked due to too many failed attempts. Please try again later.",
                        false
                );
            }

            // Validate role if specified
            if (expectedRole != null && user.getRole() != expectedRole) {
                return new AdminLoginResponse("Access denied for this role", false);
            }

            // Verify password
            if (!passwordEncoder.matches(request.getPassword(), user.getHashedPassword())) {
                handleFailedLogin(user);
                return new AdminLoginResponse("Invalid email or password", false);
            }

            // ✅ FIXED: Check if must reset password for HR/Faculty with temporary passwords
            if (Boolean.TRUE.equals(user.getMustResetPassword())
                    && (user.getRole() == User.Role.HR || user.getRole() == User.Role.FACULTY || user.getRole() == User.Role.INTERVIEW_PANELIST)) {

                log.info("User {} must reset password, generating reset token", user.getEmail());

                // ✅ Use the correct method for generating reset token
                String token = passwordResetService.generateResetTokenForUser(user.getEmail());

                // ✅ Send invitation/reset email
                emailService.sendInvitation(user.getEmail(), token, 24);

                // ✅ Return response indicating password reset is required
                AdminLoginResponse response = new AdminLoginResponse(
                        null,
                        "MUST_RESET_PASSWORD",
                        user.getRole().name(),
                        user.getUserId(),
                        user.getEmail(),
                        user.getStatus().name()
                );
                response.setSuccess(true);
                response.setMustResetPassword(true);
                log.info("Password reset email sent to {}", user.getEmail());
                return response;
            }

            // Reset failed attempts on successful login
            resetFailedAttempts(user);

            // Generate JWT token for successful login
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

            // Create successful response
            AdminLoginResponse response = new AdminLoginResponse(
                    token,
                    "Login successful",
                    user.getRole().name(),
                    user.getUserId(),
                    user.getEmail(),
                    user.getStatus().name()
            );
            response.setSuccess(true);
            response.setMustResetPassword(false);

            log.info("Login successful for user: {} with role: {}", user.getEmail(), user.getRole());
            return response;

        } catch (Exception e) {
            log.error("Login failed for {}: {}", request.getEmail(), e.getMessage());
            return new AdminLoginResponse("Login failed: " + e.getMessage(), false);
        }
    }

    @Override
    public AdminLoginResponse loginAsUser(AdminLoginRequest request) {
        log.info("🔍 Attempting unified login for email: {}", request.getEmail());

        try {
            // ✅ STEP 1: Check User table first
            Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                log.info("✅ Found user in User table - Email: {}, Role: {}, MustResetPassword: {}",
                        user.getEmail(), user.getRole(), user.getMustResetPassword());

                // Check if user is active
                if (user.getStatus() != User.Status.ACTIVE) {
                    log.warn("❌ User {} has status: {}", request.getEmail(), user.getStatus());
                    return new AdminLoginResponse("Account is " + user.getStatus().toString().toLowerCase(), false);
                }

                // Check if account is locked
                if (isAccountLocked(user)) {
                    return new AdminLoginResponse(
                            "Account is temporarily locked due to too many failed attempts. Please try again later.",
                            false
                    );
                }

                // Verify password
                if (!passwordEncoder.matches(request.getPassword(), user.getHashedPassword())) {
                    log.warn("❌ Invalid password for user: {}", request.getEmail());
                    handleFailedLogin(user);
                    return new AdminLoginResponse("Invalid credentials", false);
                }

                // ✅ FIXED: Check if must reset password for HR/Faculty
                if (Boolean.TRUE.equals(user.getMustResetPassword())
                        && (user.getRole() == User.Role.HR || user.getRole() == User.Role.FACULTY || user.getRole() == User.Role.INTERVIEW_PANELIST)) {

                    log.info("User {} must reset password, generating reset token", user.getEmail());

                    // ✅ Use the correct method
                    String token = passwordResetService.generateResetTokenForUser(user.getEmail());
                    emailService.sendInvitation(user.getEmail(), token, 24);

                    AdminLoginResponse response = new AdminLoginResponse(
                            null,
                            "MUST_RESET_PASSWORD",
                            user.getRole().name(),
                            user.getUserId(),
                            user.getEmail(),
                            user.getStatus().name()
                    );
                    response.setSuccess(true);
                    response.setMustResetPassword(true);
                    log.info("Password reset required response sent for {}", user.getEmail());
                    return response;
                }

                // Reset failed attempts and generate token for successful login
                resetFailedAttempts(user);
                String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

                log.info("✅ User login successful - Email: {}, Role: {}", user.getEmail(), user.getRole());

                AdminLoginResponse response = new AdminLoginResponse(
                        token,
                        "Login successful",
                        user.getRole().name(),
                        user.getUserId(),
                        user.getEmail(),
                        user.getStatus().name()
                );
                response.setSuccess(true);
                response.setMustResetPassword(false);
                return response;
            }

            // ✅ STEP 2: Check Student table if not found in User table
            log.info("🔍 User not found in User table, checking Student table...");
            Optional<Student> studentOptional = studentRepository.findByEmail(request.getEmail());

            if (studentOptional.isPresent()) {
                Student student = studentOptional.get();
                log.info("✅ Found student in Student table - Email: {}, Role: {}", student.getEmail(), student.getRole());

                // Check if student registration is complete
                if (student.getHashedPassword() == null || student.getHashedPassword().isEmpty()) {
                    log.warn("❌ Student registration incomplete for: {}", request.getEmail());
                    return new AdminLoginResponse("Registration incomplete. Please complete your registration first.", false);
                }

                // Verify password
                if (!passwordEncoder.matches(request.getPassword(), student.getHashedPassword())) {
                    log.warn("❌ Invalid password for student: {}", request.getEmail());
                    return new AdminLoginResponse("Invalid credentials", false);
                }

                // Generate JWT token with student role
                String studentRole = student.getRole() != null ? student.getRole().name() : "STUDENT";
                String token = jwtUtil.generateToken(student.getEmail(), studentRole);

                log.info("✅ Student login successful - Email: {}, Role: {}", student.getEmail(), studentRole);

                AdminLoginResponse response = new AdminLoginResponse(
                        token,
                        "Login successful",
                        studentRole,
                        student.getId(),
                        student.getEmail(),
                        "ACTIVE"
                );
                response.setSuccess(true);
                response.setMustResetPassword(false);
                return response;
            }

            // ✅ STEP 3: Email not found in either table
            log.warn("❌ Email not found in any table: {}", request.getEmail());
            return new AdminLoginResponse("Invalid email or password", false);

        } catch (Exception e) {
            log.error("❌ Login error for {}: {}", request.getEmail(), e.getMessage(), e);
            return new AdminLoginResponse("Login failed: " + e.getMessage(), false);
        }
    }

    @Override
    public User createUserWithTemporaryPassword(String email, User.Role role) {
        if (role == User.Role.ADMIN) {
            throw new RuntimeException("Cannot create admin users through this method");
        }

        return passwordResetService.createUserWithTemporaryPassword(email, role);
    }

    private boolean isAccountLocked(User user) {
        return user.getAccountLockedUntil() != null &&
                user.getAccountLockedUntil().isAfter(LocalDateTime.now());
    }

    private void handleFailedLogin(User user) {
        int failedAttempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
        user.setFailedLoginAttempts(failedAttempts);
        user.setLastFailedAttempt(LocalDateTime.now());

        // Lock account after max failed attempts
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES));
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        user.setFailedLoginAttempts(0);
        user.setLastFailedAttempt(null);
        user.setAccountLockedUntil(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}