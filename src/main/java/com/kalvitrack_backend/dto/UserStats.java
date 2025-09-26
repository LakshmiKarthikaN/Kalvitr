package com.kalvitrack_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class UserStats {
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long hrUsers;
    private long facultyUsers;
    private long interviewPanelistUsers;

}

