package com.kalvitrack_backend.repository;


import com.kalvitrack_backend.entity.InterviewerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// InterviewerAvailabilityRepository
@Repository
public interface InterviewerAvailabilityRepository extends JpaRepository<InterviewerAvailability, Long> {

    List<InterviewerAvailability> findByInterviewerIdAndIsActiveOrderByAvailableDateAscStartTimeAsc(
            Long interviewerId, Boolean isActive);

    List<InterviewerAvailability> findByAvailableDateBetweenAndIsBookedAndIsActiveOrderByAvailableDateAscStartTimeAsc(
            LocalDate startDate, LocalDate endDate, Boolean isBooked, Boolean isActive);

    List<InterviewerAvailability> findByInterviewerIdAndAvailableDateAndIsActive(
            Long interviewerId, LocalDate availableDate, Boolean isActive);

    @Modifying
    @Query("DELETE FROM InterviewerAvailability ia WHERE ia.interviewerId = :interviewerId AND ia.availableDate = :availableDate")
    void deleteByInterviewerIdAndAvailableDate(@Param("interviewerId") Long interviewerId,
                                               @Param("availableDate") LocalDate availableDate);

    @Query("SELECT ia FROM InterviewerAvailability ia " +
            "WHERE ia.isActive = true " +
            "AND ia.isBooked = false " +
            "AND (:startDate IS NULL OR ia.availableDate >= :startDate) " +
            "AND (:endDate IS NULL OR ia.availableDate <= :endDate) " +
            "ORDER BY ia.availableDate ASC, ia.startTime ASC")
    List<InterviewerAvailability> findAvailableSlots(@Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(ia) FROM InterviewerAvailability ia WHERE " +
            "ia.interviewerId = :interviewerId AND ia.availableDate = :date AND ia.isBooked = true")
    Long countBookedSlotsForInterviewerOnDate(@Param("interviewerId") Long interviewerId,
                                              @Param("date") LocalDate date);
}
