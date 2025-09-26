package com.kalvitrack_backend.service;


import com.kalvitrack_backend.dto.*;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        try {
            // Validate input
            if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
                return new CreateUserResponse(false, "Name  is required", null);
            }
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return new CreateUserResponse(false, "Email is required", null);
            }

            if (request.getPassword() == null || request.getPassword().length() < 6) {
                return new CreateUserResponse(false, "Password must be at least 6 characters long", null);
            }

            if (request.getRole() == null ||
                    (!request.getRole().equals("HR") &&
                            !request.getRole().equals("FACULTY") &&
                            !request.getRole().equals("INTERVIEW_PANELIST"))) {
                return new CreateUserResponse(false, "Role must be HR, FACULTY, or INTERVIEW_PANELIST", null);
            }

            // Check if user already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                return new CreateUserResponse(false, "User with this email already exists", null);
            }

            // Create new user
            User user = new User();
            user.setFullName(request.getFullName().trim());
            user.setEmail(request.getEmail().toLowerCase().trim());
            user.setHashedPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole(User.Role.valueOf(request.getRole()));
            user.setStatus(User.Status.ACTIVE);
            user.setMustResetPassword(true); // Admin-created users must reset password
            user.setFailedLoginAttempts(0);
            user.setResetAttemptsCount(0);

            User savedUser = userRepository.save(user);
            log.info("Created new user: {} with role: {}", savedUser.getEmail(), savedUser.getRole());

            UserResponse userResponse = convertToUserResponse(savedUser);
            return new CreateUserResponse(true, "User created successfully", userResponse);

        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            return new CreateUserResponse(false, "Failed to create user: " + e.getMessage(), null);
        }
    }

    public UsersListResponse getAllUsers() {
        try {
            List<User> users = userRepository.findAll();

            List<UserResponse> userResponses = users.stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());

            // Calculate stats
            UserStats stats = new UserStats();
            stats.setTotalUsers(users.size());
            stats.setActiveUsers(users.stream().filter(u -> u.getStatus() == User.Status.ACTIVE).count());
            stats.setInactiveUsers(users.stream().filter(u -> u.getStatus() == User.Status.INACTIVE).count());
            stats.setHrUsers(users.stream().filter(u -> u.getRole() == User.Role.HR).count());
            stats.setFacultyUsers(users.stream().filter(u -> u.getRole() == User.Role.FACULTY).count());
            stats.setInterviewPanelistUsers(users.stream().filter(u -> u.getRole() == User.Role.INTERVIEW_PANELIST).count());

            return new UsersListResponse(true, "Users retrieved successfully", userResponses, stats);

        } catch (Exception e) {
            log.error("Error fetching users: {}", e.getMessage(), e);
            return new UsersListResponse(false, "Failed to fetch users: " + e.getMessage(), null, null);
        }
    }
    // Method for ADMIN - only shows HR and FACULTY users
    public UsersListResponse getUsersForAdmin() {
        try {
            log.info("Fetching users for Admin (HR and FACULTY only)");

            List<User> users = userRepository.findByRoleIn(List.of(User.Role.HR, User.Role.FACULTY));
            // Test the query
            log.debug("Users found for admin: {}", users.size());

            List<User> allUsers = userRepository.findAll();
            log.debug("Total users in database: {}", allUsers.size());
            List<UserResponse> userResponses = users.stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());

            // Calculate stats only for HR and FACULTY users
            UserStats stats = new UserStats();
            stats.setTotalUsers(users.size());
            stats.setActiveUsers(users.stream().filter(u -> u.getStatus() == User.Status.ACTIVE).count());
            stats.setInactiveUsers(users.stream().filter(u -> u.getStatus() == User.Status.INACTIVE).count());
            stats.setHrUsers(users.stream().filter(u -> u.getRole() == User.Role.HR).count());
            stats.setFacultyUsers(users.stream().filter(u -> u.getRole() == User.Role.FACULTY).count());
            stats.setInterviewPanelistUsers(0L); // Admin can't see panelists

            return new UsersListResponse(true, "Users retrieved successfully for Admin", userResponses, stats);

        } catch (Exception e) {
            log.error("Error fetching users for admin: {}", e.getMessage(), e);
            return new UsersListResponse(false, "Failed to fetch users: " + e.getMessage(), null, null);
        }
    }
    // Method for HR - only shows FACULTY and INTERVIEW_PANELIST users
    public UsersListResponse getUsersForHR() {
        try {
            log.info("Fetching users for HR (FACULTY and INTERVIEW_PANELIST only)");

            List<User> users = userRepository.findByRoleIn(List.of(User.Role.FACULTY, User.Role.INTERVIEW_PANELIST));

            List<UserResponse> userResponses = users.stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());

            // Calculate stats only for FACULTY and INTERVIEW_PANELIST users
            UserStats stats = new UserStats();
            stats.setTotalUsers(users.size());
            stats.setActiveUsers(users.stream().filter(u -> u.getStatus() == User.Status.ACTIVE).count());
            stats.setInactiveUsers(users.stream().filter(u -> u.getStatus() == User.Status.INACTIVE).count());
            stats.setHrUsers(0L); // HR can't see other HR users
            stats.setFacultyUsers(users.stream().filter(u -> u.getRole() == User.Role.FACULTY).count());
            stats.setInterviewPanelistUsers(users.stream().filter(u -> u.getRole() == User.Role.INTERVIEW_PANELIST).count());

            return new UsersListResponse(true, "Users retrieved successfully for HR", userResponses, stats);

        } catch (Exception e) {
            log.error("Error fetching users for HR: {}", e.getMessage(), e);
            return new UsersListResponse(false, "Failed to fetch users: " + e.getMessage(), null, null);
        }
    }
    // Fallback method - returns all non-admin users (used by the general /users endpoint)

    public List<UserResponse> getUsersByRole(User.Role role) {
        try {
            List<User> users = userRepository.findByRole(role);
            return users.stream()
                    .map(this::convertToUserResponse)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching users by role {}: {}", role, e.getMessage(), e);
            return List.of();
        }
    }
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim());
    }

    @Transactional
    public boolean updateUserStatus(Long userId, User.Status status) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setStatus(status);
                userRepository.save(user);
                log.info("Updated user {} status to {}", user.getEmail(), status);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error updating user status: {}", e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public void incrementFailedLoginAttempts(String email) {
        Optional<User> userOpt = findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            user.setLastFailedAttempt(LocalDateTime.now());

            // Lock account after 5 failed attempts for 30 minutes
            if (user.getFailedLoginAttempts() >= 5) {
                user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(30));
                log.warn("Account locked for user: {} due to too many failed login attempts", email);
            }

            userRepository.save(user);
        }
    }

    @Transactional
    public void resetFailedLoginAttempts(String email) {
        Optional<User> userOpt = findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setFailedLoginAttempts(0);
            user.setLastFailedAttempt(null);
            user.setAccountLockedUntil(null);
            userRepository.save(user);
        }
    }

    private UserResponse convertToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().name());
        response.setStatus(user.getStatus().name());
        response.setMustResetPassword(user.getMustResetPassword());
        response.setFailedLoginAttempts(user.getFailedLoginAttempts());

        if (user.getCreatedAt() != null) {
            response.setCreatedAt(user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (user.getUpdatedAt() != null) {
            response.setUpdatedAt(user.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        return response;
    }
}
