package com.kalvitrack.kalvitrack_backend.controller;

import com.kalvitrack.kalvitrack_backend.dto.AdminLoginRequest;
import com.kalvitrack.kalvitrack_backend.dto.AdminLoginResponse;
import com.kalvitrack.kalvitrack_backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Add if needed for CORS
public class AuthController {

    private final AdminService adminService;

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        try {
            AdminLoginResponse response = adminService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(new AdminLoginResponse(null, e.getMessage(), null));
        }
    }
}