package com.kalvitrack_backend.service;

import com.kalvitrack_backend.dto.ValidateTokenResponse;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.entity.Student;
import com.kalvitrack_backend.repository.UserRepository;
import com.kalvitrack_backend.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Transactional
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final EmailService emailService;

    @Value("${app.password.reset.token.expiry.hours:1}")
    private int resetTokenExpiryHours;

    @Value("${app.password.reset.max.attempts:3}")
    private int maxResetAttempts;

    @Value("${app.password.reset.lockout.minutes:30}")
    private int lockoutMinutes;

    private final ConcurrentHashMap<String, AtomicInteger> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> ipLockout = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> emailAttempts = new ConcurrentHashMap<>();

    private static final int MAX_IP_ATTEMPTS_PER_HOUR = 10;
    private static final int MAX_EMAIL_ATTEMPTS_PER_HOUR = 3;

    /**
     * FIXED: Initiate password reset for both Users and Students
     * Now allows ZSGS and PMIS students to use forgot password
     */
    public String initiatePasswordReset(String email, String clientIp) {
        // Rate limiting checks
        checkRateLimit(clientIp, email);

        // First check Users table (Admin, HR, Faculty)
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Block admin users from using forgot password
            if (user.getRole() == User.Role.ADMIN) {
                logger.warn("Admin user attempted password reset: {} from IP: {}", email, clientIp);
                throw new RuntimeException("Admin accounts cannot reset password through this method. Please contact system administrator.");
            }

            // Check if user is temporarily locked
            if (isUserResetLocked(user)) {
                throw new RuntimeException("Too many reset attempts. Please try again later.");
            }

            // Generate and set reset token for User
            String resetToken = generateSecureToken();
            user.setResetPasswordToken(resetToken);
            user.setResetTokenExpiry(LocalDateTime.now().plusHours(resetTokenExpiryHours));
            user.setUpdatedAt(LocalDateTime.now());

            // Save to database BEFORE sending email
            User savedUser = userRepository.save(user);
            logger.info("✅ Reset token saved to database for user: {} - Token: {}", email, resetToken.substring(0, 10) + "...");

            // Send reset email
            emailService.sendPasswordResetLink(savedUser.getEmail(), resetToken, resetTokenExpiryHours);
            logger.info("Password reset initiated for user: {} (Role: {}) from IP: {}", email, user.getRole(), clientIp);
            return resetToken;
        }

        // If not found in Users table, check Students table
        Optional<Student> studentOpt = studentRepository.findByEmail(email);

        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();

            // FIXED: Allow ZSGS and PMIS students to use forgot password
            // Only block if student role is not ZSGS or PMIS
            if (student.getRole() != Student.StudentRole.ZSGS && student.getRole() != Student.StudentRole.PMIS) {
                logger.warn("Student with unsupported role attempted forgot password: {} (Role: {}) from IP: {}",
                        email, student.getRole(), clientIp);
                throw new RuntimeException("This account type cannot use forgot password. Please contact your administrator for password assistance.");
            }

            // Check if student is temporarily locked
            if (isStudentResetLocked(student)) {
                throw new RuntimeException("Too many reset attempts. Please try again later.");
            }

            // Generate and set reset token for Student
            String resetToken = generateSecureToken();
            student.setResetPasswordToken(resetToken);
            student.setResetTokenExpiry(LocalDateTime.now().plusHours(resetTokenExpiryHours));
            student.setUpdatedAt(LocalDateTime.now());

            // Save to database BEFORE sending email
            Student savedStudent = studentRepository.save(student);
            logger.info("✅ Reset token saved to database for student: {} - Token: {}", email, resetToken.substring(0, 10) + "...");

            // Send reset email
            emailService.sendPasswordResetLink(savedStudent.getEmail(), resetToken, resetTokenExpiryHours);
            logger.info("Password reset initiated for student: {} (Role: {}) from IP: {}", email, student.getRole(), clientIp);
            return resetToken;
        }

        // If not found in either table
        logger.warn("Password reset attempted for non-existent email: {} from IP: {}", email, clientIp);
        throw new RuntimeException("Email address not found in our records");
    }

    /**
     * FIXED: Generate reset token for HR/Faculty who must reset password
     */
    public String generateResetTokenForUser(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOpt.get();

        // Only allow for HR and Faculty
        if (user.getRole() != User.Role.HR && user.getRole() != User.Role.FACULTY && user.getRole() != User.Role.INTERVIEW_PANELIST) {
            throw new RuntimeException("This operation is only allowed for HR, Faculty and Interview panelist users");
        }

        // Generate reset token
        String resetToken = generateSecureToken();
        user.setResetPasswordToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(resetTokenExpiryHours));
        user.setUpdatedAt(LocalDateTime.now());

        // Save to database
        User savedUser = userRepository.save(user);
        logger.info("✅ Reset token generated for user: {} - Token: {}", email, resetToken.substring(0, 10) + "...");

        return resetToken;
    }

    /**
     * UPDATED: Reset password for both Users and Students
     */
    public String resetPassword(String token, String newPassword, String clientIp) {
        // First check in Users table
        Optional<User> userOpt = userRepository.findByResetPasswordToken(token);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return resetUserPassword(user, newPassword, clientIp);
        }

        // If not found in Users, check Students table
        Optional<Student> studentOpt = studentRepository.findByResetPasswordToken(token);

        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            return resetStudentPassword(student, newPassword, clientIp);
        }

        // Token not found in either table
        logger.warn("Invalid token used from IP: {}", clientIp);
        throw new RuntimeException("Invalid or expired reset token");
    }

    /**
     * UPDATED: Validate token for both Users and Students
     */
    public ValidateTokenResponse validateResetToken(String token) {
        // Check Users table first
        Optional<User> userOpt = userRepository.findByResetPasswordToken(token);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
                return new ValidateTokenResponse(false, "Token has expired", null);
            }
            return new ValidateTokenResponse(true, "Token is valid", user.getEmail());
        }

        // Check Students table
        Optional<Student> studentOpt = studentRepository.findByResetPasswordToken(token);
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            if (student.getResetTokenExpiry() == null || student.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
                return new ValidateTokenResponse(false, "Token has expired", null);
            }
            return new ValidateTokenResponse(true, "Token is valid", student.getEmail());
        }

        return new ValidateTokenResponse(false, "Invalid token", null);
    }

    // FIXED: Helper methods for User password reset
    private String resetUserPassword(User user, String newPassword, String clientIp) {
        // Check if token has expired
        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            logger.warn("Expired token used for user: {} from IP: {}", user.getEmail(), clientIp);
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        // Validate password strength
        validatePasswordStrength(newPassword);

        // Check if new password is same as current
        if (passwordEncoder.matches(newPassword, user.getHashedPassword())) {
            throw new RuntimeException("New password cannot be the same as current password");
        }

        // Reset password and clear flags
        user.setHashedPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetTokenExpiry(null);
        user.setMustResetPassword(false); // IMPORTANT: Clear this flag
        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        user.setPasswordChangedAt(LocalDateTime.now()); // Track when password was changed
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        logger.info("✅ Password reset completed for user: {} from IP: {}", user.getEmail(), clientIp);
        return "Password has been reset successfully";
    }

    // Helper methods for Student password reset
    private String resetStudentPassword(Student student, String newPassword, String clientIp) {
        // Check if token has expired
        if (student.getResetTokenExpiry() == null || student.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            logger.warn("Expired token used for student: {} from IP: {}", student.getEmail(), clientIp);
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        // Validate password strength
        validatePasswordStrength(newPassword);

        // Check if new password is same as current
        if (passwordEncoder.matches(newPassword, student.getHashedPassword())) {
            throw new RuntimeException("New password cannot be the same as current password");
        }

        // Reset password
        student.setHashedPassword(passwordEncoder.encode(newPassword));
        student.setResetPasswordToken(null);
        student.setResetTokenExpiry(null);
        student.setFailedLoginAttempts(0);
        student.setAccountLockedUntil(null);
        student.setUpdatedAt(LocalDateTime.now());

        studentRepository.save(student);
        logger.info("Password reset completed for student: {} from IP: {}", student.getEmail(), clientIp);
        return "Password has been reset successfully";
    }

    /**
     * FIXED: Create user with temporary password and set mustResetPassword flag
     */
    public User createUserWithTemporaryPassword(String email, User.Role role) {
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        if (role == User.Role.ADMIN) {
            throw new RuntimeException("Cannot create admin users through this method");
        }

        String temporaryPassword = generateTemporaryPassword();

        User user = new User();
        user.setEmail(email);
        user.setHashedPassword(passwordEncoder.encode(temporaryPassword));
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        user.setMustResetPassword(true); // IMPORTANT: Set this flag for temporary passwords
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        logger.info("✅ User created with temporary password: {} - Role: {}", email, role);

        return savedUser;
    }

    /**
     * FIXED: Invite user with proper mustResetPassword handling
     */
    public void inviteUser(String email, User.Role role) {
        if (role == User.Role.ADMIN) {
            throw new RuntimeException("Cannot invite admin users through this method");
        }

        // Check if user already exists
        Optional<User> existingUser = userRepository.findByEmail(email);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            logger.info("Updating existing user for invitation: {}", email);
        } else {
            user = new User();
            user.setEmail(email);
            user.setCreatedAt(LocalDateTime.now());
            logger.info("Creating new user for invitation: {}", email);
        }

        // Set temporary password and mandatory reset flag
        String tempPassword = generateTemporaryPassword();
        user.setHashedPassword(passwordEncoder.encode(tempPassword));
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        user.setMustResetPassword(true); // IMPORTANT: Must reset password
        user.setUpdatedAt(LocalDateTime.now());

        // Generate reset token for invitation
        String resetToken = generateSecureToken();
        user.setResetPasswordToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(24)); // 24 hours for invitation

        User savedUser = userRepository.save(user);

        // Send invitation email with reset link
        emailService.sendInvitation(email, resetToken, 24);
        logger.info("✅ Invitation sent to {} with role {} - Reset token: {}",
                email, role, resetToken.substring(0, 10) + "...");
    }

    /**
     * Check if user must reset password
     */
    public boolean mustResetPassword(User user) {
        boolean mustReset = Boolean.TRUE.equals(user.getMustResetPassword());
        logger.info("Checking mustResetPassword for {}: {}", user.getEmail(), mustReset);
        return mustReset;
    }

    /**
     * Get user by ID
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Helper method to check if user is locked
    private boolean isUserResetLocked(User user) {
        return user.getAccountLockedUntil() != null && user.getAccountLockedUntil().isAfter(LocalDateTime.now());
    }

    // Helper method to check if student is locked
    private boolean isStudentResetLocked(Student student) {
        return student.getAccountLockedUntil() != null && student.getAccountLockedUntil().isAfter(LocalDateTime.now());
    }

    private String generateSecureToken() {
        return UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "") +
                System.currentTimeMillis();
    }

    private void checkRateLimit(String clientIp, String email) {
        LocalDateTime now = LocalDateTime.now();

        ipAttempts.putIfAbsent(clientIp, new AtomicInteger(0));
        LocalDateTime ipLockUntil = ipLockout.get(clientIp);

        if (ipLockUntil != null && ipLockUntil.isAfter(now)) {
            throw new RuntimeException("Too many requests from this IP. Please try again later.");
        }

        if (ipAttempts.get(clientIp).incrementAndGet() > MAX_IP_ATTEMPTS_PER_HOUR) {
            ipLockout.put(clientIp, now.plusHours(1));
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        LocalDateTime emailLastAttempt = emailAttempts.get(email);
        if (emailLastAttempt != null && emailLastAttempt.plusHours(1).isAfter(now)) {
            throw new RuntimeException("Too many reset attempts for this email. Please try again later.");
        }

        emailAttempts.put(email, now);
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long");
        }

        if (!Pattern.compile(".*[A-Z].*").matcher(password).matches()) {
            throw new RuntimeException("Password must contain at least one uppercase letter");
        }

        if (!Pattern.compile(".*[a-z].*").matcher(password).matches()) {
            throw new RuntimeException("Password must contain at least one lowercase letter");
        }

        if (!Pattern.compile(".*[0-9].*").matcher(password).matches()) {
            throw new RuntimeException("Password must contain at least one number");
        }

        if (!Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*").matcher(password).matches()) {
            throw new RuntimeException("Password must contain at least one special character");
        }

        if (password.toLowerCase().contains("password") ||
                password.toLowerCase().contains("123456") ||
                password.toLowerCase().contains("admin")) {
            throw new RuntimeException("Password contains common patterns and is not secure");
        }
    }
    public String adminForcePasswordReset(Long userId, String clientIp) {
        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOpt.get();

        // Cannot force reset for admin users
        if (user.getRole() == User.Role.ADMIN) {
            throw new RuntimeException("Cannot force password reset for admin users");
        }

        // Set must reset password flag
        user.setMustResetPassword(true);

        // Generate reset token
        String resetToken = generateSecureToken();
        user.setResetPasswordToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(24)); // 24 hours for admin forced reset
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Send reset email
        emailService.sendPasswordResetLink(user.getEmail(), resetToken, 24);

        logger.info("Admin forced password reset for user: {} from IP: {}", user.getEmail(), clientIp);
        return "Password reset has been forced for user. They will receive an email with reset instructions.";
    }/**
     * Force reset with current password validation
     */
    public String initiateForceReset(Long userId, String currentPassword, String clientIp) {
        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOpt.get();

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getHashedPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Generate reset token
        String resetToken = generateSecureToken();
        user.setResetPasswordToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1)); // Short expiry for force reset
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        logger.info("Force reset initiated for user: {} from IP: {}", user.getEmail(), clientIp);
        return resetToken;
    }


    public String generateTemporaryPassword() {
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowerCase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specialChars = "!@#$%^&*";

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        password.append(upperCase.charAt(random.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(specialChars.charAt(random.nextInt(specialChars.length())));

        String allChars = upperCase + lowerCase + digits + specialChars;
        for (int i = 4; i < 12; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        return shuffleString(password.toString());
    }

    private String shuffleString(String input) {
        char[] chars = input.toCharArray();
        SecureRandom random = new SecureRandom();

        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }


}