package com.kalvitrack_backend.controller;


import com.kalvitrack_backend.dto.*;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(
        origins = {
                "https://kalvitrack.vercel.app",
                "https://kalvi-track.co.in",
                "https://www.kalvi-track.co.in",
                "http://localhost:3000",
                "http://localhost:5173"
        },
        allowedHeaders = "*",
        allowCredentials = "true",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}
)
public class UserController {

    private final UserService userService;

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR')") // Fixed syntax
    public ResponseEntity<CreateUserResponse> createUser(@RequestBody CreateUserRequest request) {
        try {
            log.info("Creating user with fullName: {} email: {} and role: {}", request.getFullName(),request.getEmail(), request.getRole());

            CreateUserResponse response = userService.createUser(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error in createUser endpoint: {}", e.getMessage(), e);
            CreateUserResponse errorResponse = new CreateUserResponse(
                    false,
                    "Server error: " + e.getMessage(),
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    @GetMapping("/panelists")
    @PreAuthorize("hasRole('HR') or hasRole('ADMIN')") // Fixed syntax
    public ResponseEntity<List<UserResponse>> getAllPanelists() {
        try {
            log.info("Fetching all interview panelists");

            List<UserResponse> panelists = userService.getUsersByRole(User.Role.INTERVIEW_PANELIST);
            return ResponseEntity.ok(panelists);

        } catch (Exception e) {
            log.error("Error fetching panelists: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    // FALLBACK ENDPOINT - For backward compatibility
    @GetMapping("/users")
    @PreAuthorize("hasRole('HR') or hasRole('ADMIN')")
    public ResponseEntity<UsersListResponse> getAllUsers() {
        try {
            log.info("Fallback endpoint - fetching users based on role");

            // You can determine user role from security context if needed
            // For now, return all users that the requester has access to
            UsersListResponse response = userService.getAllUsers();

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error in getAllUsers endpoint: {}", e.getMessage(), e);
            UsersListResponse errorResponse = new UsersListResponse(
                    false,
                    "Server error: " + e.getMessage(),
                    null,
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    // ADMIN ENDPOINT - Admin can only see HR and FACULTY users
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsersListResponse> getUsersForAdmin() {
        try {
            log.info("Admin fetching users (HR and FACULTY only)");

            UsersListResponse response = userService.getUsersForAdmin();

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error in getUsersForAdmin endpoint: {}", e.getMessage(), e);
            UsersListResponse errorResponse = new UsersListResponse(
                    false,
                    "Server error: " + e.getMessage(),
                    null,
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    // HR ENDPOINT - HR can only see FACULTY and INTERVIEW_PANELIST users
    @GetMapping("/hr/users")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<UsersListResponse> getUsersForHR() {
        try {
            log.info("HR fetching users (FACULTY and INTERVIEW_PANELIST only)");

            UsersListResponse response = userService.getUsersForHR();

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error in getUsersForHR endpoint: {}", e.getMessage(), e);
            UsersListResponse errorResponse = new UsersListResponse(
                    false,
                    "Server error: " + e.getMessage(),
                    null,
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }



    @PutMapping("/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam String status) {
        try {
            log.info("Updating user {} status to {}", userId, status);

            User.Status userStatus;
            try {
                userStatus = User.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(
                        new ApiResponse(false, "Invalid status. Must be ACTIVE or INACTIVE")
                );
            }

            boolean updated = userService.updateUserStatus(userId, userStatus);

            if (updated) {
                return ResponseEntity.ok(new ApiResponse(true, "User status updated successfully"));
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Error updating user status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new ApiResponse(false, "Server error: " + e.getMessage())
            );
        }
    }
}
