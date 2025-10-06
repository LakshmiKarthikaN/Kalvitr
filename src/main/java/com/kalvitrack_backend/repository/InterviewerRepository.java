package com.kalvitrack_backend.repository;

import com.kalvitrack_backend.entity.Interviewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// InterviewerRepository
@Repository
public interface InterviewerRepository extends JpaRepository<Interviewer, Long> {

    Optional<Interviewer> findByUserId(Long userId);

    List<Interviewer> findByIsActiveOrderByCreatedAtDesc(Boolean isActive);

    @Query(value = "SELECT i.* FROM interviewer i " +
            "JOIN user_roles u ON i.user_id = u.user_id " +
            "WHERE u.role IN ('INTERVIEW_PANELIST', 'FACULTY') " +
            "AND u.status = 'ACTIVE' " +
            "AND i.is_active = true",
            nativeQuery = true)
    List<Interviewer> findActiveInterviewers();



    boolean existsByUserId(Long userId);
}