package com.kalvitrack_backend.repository;

import com.kalvitrack_backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Student> findByRole(Student.StudentRole role);
    Optional<Student> findByResetPasswordToken(String resetPasswordToken);
    List<Student> findByStatus(Student.StudentStatus status);
    @Query("SELECT s FROM Student s WHERE " +
            "s.fullName IS NULL OR s.fullName = '' OR " +
            "s.hashedPassword IS NULL OR s.hashedPassword = '' OR " +
            "s.mobileNumber IS NULL OR s.mobileNumber = '' OR " +
            "s.collegeName IS NULL OR s.collegeName = '' OR " +
            "s.yearOfGraduation IS NULL OR " +
            "s.emailVerified = false")
    List<Student> findIncompleteRegistrations();

    @Query("SELECT s FROM Student s WHERE " +
            "s.fullName IS NOT NULL AND s.fullName != '' AND " +
            "s.hashedPassword IS NOT NULL AND s.hashedPassword != '' AND " +
            "s.mobileNumber IS NOT NULL AND s.mobileNumber != '' AND " +
            "s.collegeName IS NOT NULL AND s.collegeName != '' AND " +
            "s.yearOfGraduation IS NOT NULL AND " +
            "s.emailVerified = true")
    List<Student> findCompleteRegistrations();


    @Modifying
    @Query("UPDATE Student s SET s.failedLoginAttempts = :attempts, s.lastFailedAttempt = :lastAttempt WHERE s.email = :email")
    void updateFailedLoginAttempts(@Param("email") String email,
                                   @Param("attempts") Integer attempts,
                                   @Param("lastAttempt") LocalDateTime lastAttempt);
    // Find active students only
    @Query("SELECT s FROM Student s WHERE s.status = 'ACTIVE'")
    List<Student> findActiveStudents();

    // Find students by email verification status
    List<Student> findByEmailVerified(boolean emailVerified);

    // Custom query for dashboard statistics
    @Query("SELECT COUNT(s) FROM Student s WHERE s.emailVerified = true")
    long countVerifiedStudents();

    @Query("SELECT COUNT(s) FROM Student s WHERE s.emailVerified = false")
    long countUnverifiedStudents();
    @Modifying
    @Query("UPDATE Student s SET s.accountLockedUntil = :lockUntil WHERE s.email = :email")
    void lockAccount(@Param("email") String email, @Param("lockUntil") LocalDateTime lockUntil);

    @Modifying
    @Query("UPDATE Student s SET s.failedLoginAttempts = 0, s.lastFailedAttempt = NULL, s.accountLockedUntil = NULL WHERE s.email = :email")
    void resetLoginAttempts(@Param("email") String email);

    @Modifying
    @Query("UPDATE Student s SET s.lastLogin = :loginTime WHERE s.email = :email")
    void updateLastLogin(@Param("email") String email, @Param("loginTime") LocalDateTime loginTime);

    long countByRole(Student.StudentRole role);

    long countByStatus(Student.StudentStatus status);
}