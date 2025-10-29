package com.kalvitrack_backend.dto.scheduling;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ScheduleInterviewDTO {
    private Long studentId;
    private Long interviewerId;
    private Long availabilityId; // The specific slot being booked
    private LocalDate date;
    private String startTime;
    private String endTime;
    private String remarks;
}