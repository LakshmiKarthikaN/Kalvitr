package com.kalvitrack.kalvitrack_backend.dto;

import lombok.Getter;
import lombok.Setter;
@Setter
@Getter

public class AdminLoginRequest {
    private String email;
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
