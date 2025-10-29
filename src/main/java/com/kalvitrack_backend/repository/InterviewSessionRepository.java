package com.kalvitrack_backend.repository;

import com.kalvitrack_backend.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    // ✅ FIXED: Parameter name matches @Param
    @Query("SELECT COUNT(i) > 0 FROM InterviewSession i WHERE i.studentId = :studentId AND i.isActive = true AND i.sessionStatus NOT IN ('CANCELLED', 'COMPLETED')")
    boolean existsActiveInterviewForStudent(@Param("studentId") Long studentId);

    // ✅ Already correct
    @Query("SELECT COUNT(i) > 0 FROM InterviewSession i WHERE i.interviewerId = :interviewerId " +
            "AND i.interviewDate = :date AND i.isActive = true " +
            "AND i.sessionStatus NOT IN ('CANCELLED') " +
            "AND ((i.startTime <= :startTime AND i.endTime > :startTime) " +
            "OR (i.startTime < :endTime AND i.endTime >= :endTime) " +
            "OR (i.startTime >= :startTime AND i.endTime <= :endTime))")
    boolean hasOverlappingSession(@Param("interviewerId") Long interviewerId,
                                  @Param("date") LocalDate date,
                                  @Param("startTime") LocalTime startTime,
                                  @Param("endTime") LocalTime endTime);

    // ✅ Already correct
    @Query("SELECT i FROM InterviewSession i WHERE i.isActive = true ORDER BY i.interviewDate DESC, i.startTime DESC")
    List<InterviewSession> findAllScheduledInterviews();

    // ✅ FIXED: Parameter name changed from "id" to "studentId"
    List<InterviewSession> findByStudentIdAndIsActiveOrderByInterviewDateDesc(Long studentId, Boolean isActive);

    // ✅ Already correct
    List<InterviewSession> findByInterviewerIdAndIsActiveOrderByInterviewDateDesc(Long interviewerId, Boolean isActive);

    // ✅ Already correct
    List<InterviewSession> findByScheduledByHrAndIsActiveOrderByInterviewDateDesc(Long scheduledByHr, Boolean isActive);

    // ✅ Already correct
    @Query("SELECT i FROM InterviewSession i WHERE i.isActive = true " +
            "AND i.interviewDate >= :today " +
            "AND i.sessionStatus IN ('SCHEDULED', 'LINK_ADDED') " +
            "ORDER BY i.interviewDate ASC, i.startTime ASC")
    List<InterviewSession> findUpcomingInterviews(@Param("today") LocalDate today);
}