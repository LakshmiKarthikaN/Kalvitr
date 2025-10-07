package com.kalvitrack_backend.controller;

import com.kalvitrack_backend.dto.AdminLoginRequest;
import com.kalvitrack_backend.dto.AdminLoginResponse;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {  "http://kalvitrack.vercel.app"})
public class AuthController {

    private final AdminService adminService;

    /**
     * ✅ UNIFIED LOGIN - Checks both User and Student tables
     */
    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        log.info("=== UNIFIED LOGIN REQUEST ===");
        log.info("Email: {}", request.getEmail());

        AdminLoginResponse response = adminService.loginAsUser(request);

        if (response.isSuccess()) {
            log.info("✅ Login successful - Role: {}", response.getRole());
            return ResponseEntity.ok(response);
        } else {
            log.warn("❌ Login failed: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * ✅ ROLE-SPECIFIC LOGIN ENDPOINTS (for backward compatibility)
     */
    @PostMapping("/admin/login")
    public ResponseEntity<AdminLoginResponse> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("=== ADMIN LOGIN REQUEST ===");
        log.info("Email: {}", request.getEmail());

        AdminLoginResponse response = adminService.loginByRole(request, User.Role.ADMIN);

        if (response.isSuccess()) {
            log.info("✅ Admin login successful");
            return ResponseEntity.ok(response);
        } else {
            log.warn("❌ Admin login failed: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    @PostMapping("/panelists/login")
    public ResponseEntity<AdminLoginResponse> interviewPanelistLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("=== Interview Panelist LOGIN REQUEST ===");
        log.info("Email: {}", request.getEmail());

        AdminLoginResponse response = adminService.loginByRole(request, User.Role.INTERVIEW_PANELIST);

        if (response.isSuccess()) {
            log.info("✅ Interview Panelist login successful");
            return ResponseEntity.ok(response);
        } else {
            log.warn("❌ Interview Panelist login failed: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/hr/login")
    public ResponseEntity<AdminLoginResponse> hrLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("=== HR LOGIN REQUEST ===");
        log.info("Email: {}", request.getEmail());

        AdminLoginResponse response = adminService.loginByRole(request, User.Role.HR);

        if (response.isSuccess()) {
            log.info("✅ HR login successful");
            return ResponseEntity.ok(response);
        } else {
            log.warn("❌ HR login failed: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/faculty/login")
    public ResponseEntity<AdminLoginResponse> facultyLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("=== FACULTY LOGIN REQUEST ===");
        log.info("Email: {}", request.getEmail());

        AdminLoginResponse response = adminService.loginByRole(request, User.Role.FACULTY);

        if (response.isSuccess()) {
            log.info("✅ Faculty login successful");
            return ResponseEntity.ok(response);
        } else {
            log.warn("❌ Faculty login failed: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/student/login")
    public ResponseEntity<AdminLoginResponse> studentLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("=== STUDENT LOGIN REQUEST ===");
        log.info("Email: {}", request.getEmail());

        // For students, we use the unified login which checks Student table
        AdminLoginResponse response = adminService.loginAsUser(request);

        // Ensure it's actually a student role
        if (response.isSuccess() && (
                "ZSGS".equals(response.getRole()) ||
                        "PMIS".equals(response.getRole()) ||
                        "STUDENT".equals(response.getRole()))) {
            log.info("✅ Student login successful - Role: {}", response.getRole());
            return ResponseEntity.ok(response);
        } else if (response.isSuccess()) {
            log.warn("❌ Non-student trying to use student login: {}", response.getRole());
            return ResponseEntity.badRequest().body(
                    new AdminLoginResponse("Access denied. This is a student-only login endpoint.", false)
            );
        } else {
            log.warn("❌ Student login failed: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}