package com.kalvitrack_backend.dto;

import com.kalvitrack_backend.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UsersListResponse {
    private boolean success;
    private String message;
    private List<UserResponse> users;
    private UserStats stats;

}
