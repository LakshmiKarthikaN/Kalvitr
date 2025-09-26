package com.kalvitrack_backend.service.studentregistration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AuthService {

    /**
     * Check if current user has any of the specified roles
     */
    public boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            log.debug("No authenticated user found");
            return false;
        }

        Set<String> userRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .collect(Collectors.toSet());

        boolean hasRole = java.util.Arrays.stream(roles)
                .anyMatch(userRoles::contains);

        log.debug("User {} has roles: {}, checking for: {}, result: {}",
                auth.getName(), userRoles, java.util.Arrays.toString(roles), hasRole);

        return hasRole;
    }

    /**
     * Check if current user can manage students (HR/ADMIN functionality)
     */
    public boolean canManageStudents() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            log.debug("No authenticated user for student management");
            return false;
        }

        // Check if user has ADMIN or HR role
        boolean canManage = hasAnyRole("ADMIN", "HR");

        log.debug("User {} can manage students: {}", auth.getName(), canManage);

        return canManage;
    }

    /**
     * Check if current user is an admin
     */
    public boolean isAdmin() {
        return hasAnyRole("ADMIN");
    }

    /**
     * Check if current user is HR
     */
    public boolean isHR() {
        return hasAnyRole("HR");
    }

    /**
     * Check if current user is Faculty
     */
    public boolean isFaculty() {
        return hasAnyRole("FACULTY");
    }

    /**
     * Check if current user is a student (ZSGS or PMIS)
     */
    public boolean isStudent() {
        return hasAnyRole("ZSGS", "PMIS");
    }

    /**
     * Get current user's role
     */
    public String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }

        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .findFirst()
                .orElse(null);

        log.debug("Current user role: {}", role);
        return role;
    }

    /**
     * Get current username (email)
     */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    /**
     * Get all user roles
     */
    public List<String> getCurrentUserRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return List.of();
        }

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .collect(Collectors.toList());
    }

    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());
    }
}