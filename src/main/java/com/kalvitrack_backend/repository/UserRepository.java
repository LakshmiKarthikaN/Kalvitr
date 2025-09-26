package com.kalvitrack_backend.repository;

import com.kalvitrack_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByResetPasswordToken(String resetPasswordToken);
    Optional<User> findByEmailAndStatus(String email, User.Status status);
    @Query("SELECT u FROM User u WHERE u.role IN ('HR', 'FACULTY') ")
    List<User> findAllNonAdminUsers();

    List<User> findByRole(User.Role role);

    List<User> findByStatus(User.Status status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.status = 'ACTIVE'")
    Long countActiveUsersByRole(User.Role role);
    boolean existsByEmail(String email);

    long countByRole(User.Role role);

    long countByStatus(User.Status status);

    long countByRoleAndStatus(User.Role role, User.Status status);
    // Find users by multiple roles (for specific role-based access)
    List<User> findByRoleIn(List<User.Role> roles);

    // Find users excluding specific role
    List<User> findByRoleNot(User.Role role);

    // Custom query for admin dashboard - only HR and FACULTY

    // Custom query for HR dashboard - only FACULTY and INTERVIEW_PANELIST
    @Query("SELECT u FROM User u WHERE u.role IN ('FACULTY', 'INTERVIEW_PANELIST')")
    List<User> findUsersForHR();
}