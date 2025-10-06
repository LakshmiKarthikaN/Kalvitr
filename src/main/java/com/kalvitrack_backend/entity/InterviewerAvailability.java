package com.kalvitrack_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviewer_availability")
@Getter
@Setter
@NoArgsConstructor
public class InterviewerAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "availability_id")
    private Long availabilityId;

    @Column(name = "interviewer_id", nullable = false)
    private Long interviewerId;

    @Column(name = "available_date", nullable = false)
    private LocalDate availableDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "is_booked", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isBooked = false;

    @Column(name = "slot_duration_minutes", columnDefinition = "INT DEFAULT 60")
    private Integer slotDurationMinutes = 60;

    @Column(name = "max_concurrent_interviews", columnDefinition = "INT DEFAULT 1")
    private Integer maxConcurrentInterviews = 1;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean isActive = true;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;



    public InterviewerAvailability(Long interviewerId, LocalDate availableDate,
                                   LocalTime startTime, LocalTime endTime) {
        this();
        this.interviewerId = interviewerId;
        this.availableDate = availableDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }






    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @Override
    public String toString() {
        return "InterviewerAvailability{" +
                "availabilityId=" + availabilityId +
                ", interviewerId=" + interviewerId +
                ", availableDate=" + availableDate +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", isBooked=" + isBooked +
                ", slotDurationMinutes=" + slotDurationMinutes +
                ", maxConcurrentInterviews=" + maxConcurrentInterviews +
                ", notes='" + notes + '\'' +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}