package com.kalvitrack.kalvitrack_backend.service.impl;

import com.kalvitrack.kalvitrack_backend.config.jwthandler.JwtUtil;
import com.kalvitrack.kalvitrack_backend.entity.User;
import com.kalvitrack.kalvitrack_backend.repository.UserRepository;
import com.kalvitrack.kalvitrack_backend.service.AdminService;
import com.kalvitrack.kalvitrack_backend.dto.AdminLoginRequest;
import com.kalvitrack.kalvitrack_backend.dto.AdminLoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public User createAdmin(User user) {
        user.setHashedPassword(passwordEncoder.encode(user.getHashedPassword()));
        user.setRole(User.Role.ADMIN);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getHashedPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (user.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Not an Admin");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return new AdminLoginResponse(
                token,
                "Login successful",
                user.getRole().name()
        );
    }
}
