package com.kalvitrack.kalvitrack_backend.service;

import com.kalvitrack.kalvitrack_backend.dto.AdminLoginRequest;
import com.kalvitrack.kalvitrack_backend.dto.AdminLoginResponse;
import com.kalvitrack.kalvitrack_backend.entity.User;


public interface AdminService {
    User createAdmin(User user);
    AdminLoginResponse login(AdminLoginRequest request);
}
