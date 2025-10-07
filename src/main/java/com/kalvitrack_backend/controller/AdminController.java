package com.kalvitrack_backend.controller;


import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = {" http://kalvitrack.vercel.app"})
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Simple admin login without JWT for now
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> adminLogin(@RequestBody Map<String, String> credentials) {
        Map<String, Object> response = new HashMap<>();

        try {
            String email = credentials.get("email");
            String password = credentials.get("password");

            if (email == null || password == null) {
                response.put("success", false);
                response.put("message", "Email and password are required");
                return ResponseEntity.badRequest().body(response);
            }

            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Invalid credentials");
                return ResponseEntity.status(401).body(response);
            }

            User user = userOpt.get();

            // Check if user is admin
            if (!user.getRole().equals(User.Role.ADMIN)) {
                response.put("success", false);
                response.put("message", "Access denied: Admin role required");
                return ResponseEntity.status(403).body(response);
            }

            // Verify password
            if (!passwordEncoder.matches(password, user.getHashedPassword())) {
                response.put("success", false);
                response.put("message", "Invalid credentials");
                return ResponseEntity.status(401).body(response);
            }

            // Login successful
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("user", Map.of(
                    "id", user.getUserId(),
                    "email", user.getEmail(),
                    "role", user.getRole(),
                    "status", user.getStatus()
            ));

            // For now, return a simple token (in production, use proper JWT)
            response.put("token", "admin_" + user.getUserId() + "_" + System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Login failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Create initial admin user if none exists
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initializeAdmin(@RequestBody Map<String, String> adminData) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Check if any admin already exists
            long adminCount = userRepository.countByRole(User.Role.ADMIN);
            if (adminCount > 0) {
                response.put("success", false);
                response.put("message", "Admin user already exists");
                return ResponseEntity.badRequest().body(response);
            }

            String email = adminData.get("email");
            String password = adminData.get("password");

            if (email == null || password == null) {
                response.put("success", false);
                response.put("message", "Email and password are required");
                return ResponseEntity.badRequest().body(response);
            }

            // Create admin user
            User admin = new User();
            admin.setEmail(email);
            admin.setHashedPassword(passwordEncoder.encode(password));
            admin.setRole(User.Role.ADMIN);
            admin.setStatus(User.Status.ACTIVE);
            admin.setCreatedAt(java.time.LocalDateTime.now());

            User savedAdmin = userRepository.save(admin);

            response.put("success", true);
            response.put("message", "Admin user created successfully");
            response.put("admin", Map.of(
                    "id", savedAdmin.getUserId(),
                    "email", savedAdmin.getEmail(),
                    "role", savedAdmin.getRole()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to create admin: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}