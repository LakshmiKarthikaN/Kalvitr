package com.kalvitrack_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_sessions")
@Getter
@Setter
@NoArgsConstructor
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "interviewer_id", nullable = false)
    private Long interviewerId;

    @Column(name = "scheduled_by_hr", nullable = false)
    private Long scheduledByHr;

    @Column(name = "interview_date", nullable = false)
    private LocalDate interviewDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "link_added_at")
    private LocalDateTime linkAddedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", columnDefinition = "ENUM('SCHEDULED', 'LINK_ADDED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED', 'NO_SHOW') DEFAULT 'SCHEDULED'")
    private SessionStatus sessionStatus = SessionStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_result")
    private InterviewResult interviewResult;

    @Column(name = "result_updated_at")
    private LocalDateTime resultUpdatedAt;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean isActive = true;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public enum SessionStatus {
        SCHEDULED, LINK_ADDED, COMPLETED, CANCELLED, RESCHEDULED, NO_SHOW
    }

    public enum InterviewResult {
        SELECTED, REJECTED, WAITING_LIST
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}