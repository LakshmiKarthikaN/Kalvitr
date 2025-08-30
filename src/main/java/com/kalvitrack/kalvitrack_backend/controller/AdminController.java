package com.kalvitrack.kalvitrack_backend.controller;

import com.kalvitrack.kalvitrack_backend.dto.AdminLoginRequest;
import com.kalvitrack.kalvitrack_backend.dto.AdminLoginResponse;
import com.kalvitrack.kalvitrack_backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @PostMapping("/login")
    public AdminLoginResponse login(@RequestBody AdminLoginRequest request) {
        return adminService.login(request);
    }
}
