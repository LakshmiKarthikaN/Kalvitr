package com.kalvitrack_backend.config.jwthandler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtUtil jwtUtil;

    // Comprehensive list of public endpoints that don't require authentication
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            // Authentication endpoints
            "/api/auth/login",
            "/api/auth/admin/login",
            "/api/auth/hr/login",
            "/api/auth/faculty/login",
            "/api/auth/panelists/login",

            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/validate-reset-token",
            "/api/admin/login", // Legacy endpoint

            // Student registration endpoints (public)
            "/api/students/verify-email",
            "/api/students/complete-registration",

            // Password reset endpoints
            "/api/password-reset",
            "/api/password-reset/initiate",
            "/api/password-reset/complete",
            "/api/password-reset/validate-token",

            // Health and monitoring
            "/api/health",
            "/actuator",

            // Documentation
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars",

            // Static resources
            "/public",
            "/static",
            "/favicon.ico"
    );

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("Processing request - Method: {}, URI: {}", method, requestURI);
        logger.info("=== INCOMING REQUEST ===");
        logger.info("Method: {}, URI: {}", method, requestURI);
        logger.info("All Headers:");
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logger.info("  {}: {}", headerName, request.getHeader(headerName));
        }
        logger.info("========================");
        // Skip OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(method)) {
            logger.debug("Skipping JWT validation for OPTIONS request");
            filterChain.doFilter(request, response);
            return;
        }

        // Check if this is an excluded path
        if (isExcludedPath(requestURI)) {
            logger.debug("Skipping JWT validation for excluded path: {}", requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        // Check for Authorization header
        if (authHeader == null) {
            authHeader = request.getHeader("authorization"); // Check lowercase
        }

        String token = authHeader.substring(7);
        String email = null;
        String role = null;

        try {
            // Extract email and role from token
            email = jwtUtil.extractEmail(token);
            role = jwtUtil.extractRole(token);

            logger.debug("Extracted email: {} and role: {} from token", email, role);
         // Validate extracted data
            if (email == null || email.trim().isEmpty()) {
                logger.warn("Email is null or empty in token");
                sendUnauthorizedResponse(response, "Invalid token", "Token does not contain valid email");
                return;
            }

            if (role == null || role.trim().isEmpty()) {
                logger.warn("Role is null or empty in token");
                sendUnauthorizedResponse(response, "Invalid token", "Token does not contain valid role");
                return;
            }

        } catch (Exception e) {
            logger.error("Failed to extract data from token: {}", e.getMessage());
            sendUnauthorizedResponse(response, "Invalid token", "Token format is invalid or expired");
            return;
        }

        // Proceed with authentication if user is not already authenticated
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                // Validate token
                if (jwtUtil.validateToken(token, email)) {
                    logger.debug("Token validated successfully for user: {} with role: {}", email, role);

                    // Create authorities with ROLE_ prefix for Spring Security
                    List<GrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
                    );
                    logger.debug("Raw role from token: '{}'", role);
                    logger.debug("Authorities being set: {}", authorities);

                    logger.debug("Setting authorities: {}", authorities);

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);

                    // Set additional details
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    logger.debug("Authentication set successfully for user: {} with authorities: {}",
                            email, authorities);
                    // And after setting authentication
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    logger.debug("Current authentication: {}", auth);
                    logger.debug("Current authorities: {}", auth != null ? auth.getAuthorities() : "null");

                } else {
                    logger.warn("Token validation failed for user: {}", email);
                    sendUnauthorizedResponse(response, "Token validation failed", "Token is expired or invalid. Please login again");
                    return;
                }

            } catch (Exception e) {
                logger.error("Error during token validation for user {}: {}", email, e.getMessage());
                sendUnauthorizedResponse(response, "Authentication error", "Please login again");
                return;
            }
        }

        // Continue with the request
        filterChain.doFilter(request, response);
    }

    /**
     * Check if the given path should be excluded from JWT validation
     */
    private boolean isExcludedPath(String path) {
        if (path == null) {
            return false;
        }

        boolean isExcluded = EXCLUDED_PATHS.stream().anyMatch(excludedPath ->
                path.startsWith(excludedPath) || path.equals(excludedPath)
        );

        logger.debug("Path {} is excluded: {}", path, isExcluded);
        return isExcluded;
    }

    /**
     * Send a standardized unauthorized response
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String error, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Add CORS headers to unauthorized responses
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");

        String jsonResponse = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"success\":false,\"timestamp\":\"%s\",\"status\":401}",
                error, message, Instant.now().toString()
        );

        response.getWriter().write(jsonResponse);
        logger.debug("Sent unauthorized response: {}", jsonResponse);
    }

    /**
     * Alternative method to check public endpoints (kept for compatibility)
     */
    private boolean isPublicEndpoint(String path) {
        return isExcludedPath(path);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Additional check to skip filter for certain conditions
        String requestURI = request.getRequestURI();

        // Skip filtering for static resources and health checks
        if (requestURI.startsWith("/actuator/") ||
                requestURI.startsWith("/static/") ||
                requestURI.startsWith("/webjars/") ||
                requestURI.equals("/favicon.ico")) {
            return true;
        }

        return false;
    }
}