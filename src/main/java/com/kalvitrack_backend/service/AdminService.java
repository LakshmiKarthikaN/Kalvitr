package com.kalvitrack_backend.service;


import com.kalvitrack_backend.dto.AdminLoginRequest;
import com.kalvitrack_backend.dto.AdminLoginResponse;
import com.kalvitrack_backend.entity.User;

public interface AdminService {
    User createAdmin(User user);

    AdminLoginResponse login(AdminLoginRequest request);

    AdminLoginResponse loginByRole(AdminLoginRequest request, User.Role expectedRole);

    // ✅ Add the new unified login method for checking both User and Student tables
    AdminLoginResponse loginAsUser(AdminLoginRequest request);

    // ✅ Add method for creating users with temporary passwords
    User createUserWithTemporaryPassword(String email, User.Role role);
}