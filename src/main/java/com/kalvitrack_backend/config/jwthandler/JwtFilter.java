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
            "/api/admin/login",

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
            "/health",
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

        // ✅ CRITICAL: Skip OPTIONS requests immediately (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            logger.debug("✅ Skipping JWT validation for OPTIONS request: {}", requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ CRITICAL: Check if this is an excluded (public) path BEFORE checking for Authorization
        if (isExcludedPath(requestURI)) {
            logger.debug("✅ Public endpoint - skipping JWT validation for: {}", requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ CRITICAL: Only require Authorization header for protected endpoints
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("❌ No valid Authorization header found for protected endpoint: {}", requestURI);
            sendUnauthorizedResponse(request, response, "Missing Authorization",
                    "Authorization header with Bearer token required");
            return;
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
                logger.warn("❌ Email is null or empty in token");
                sendUnauthorizedResponse(request, response, "Invalid token",
                        "Token does not contain valid email");
                return;
            }

            if (role == null || role.trim().isEmpty()) {
                logger.warn("❌ Role is null or empty in token");
                sendUnauthorizedResponse(request, response, "Invalid token",
                        "Token does not contain valid role");
                return;
            }

        } catch (Exception e) {
            logger.error("❌ Failed to extract data from token: {}", e.getMessage());
            sendUnauthorizedResponse(request, response, "Invalid token",
                    "Token format is invalid or expired");
            return;
        }

        // Proceed with authentication if user is not already authenticated
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                // Validate token
                if (jwtUtil.validateToken(token, email)) {
                    logger.debug("✅ Token validated successfully for user: {} with role: {}", email, role);

                    // Create authorities with ROLE_ prefix for Spring Security
                    List<GrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
                    );
                    logger.debug("Raw role from token: '{}'", role);
                    logger.debug("Authorities being set: {}", authorities);

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);

                    // Set additional details
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    logger.debug("✅ Authentication set successfully for user: {} with authorities: {}",
                            email, authorities);

                } else {
                    logger.warn("❌ Token validation failed for user: {}", email);
                    sendUnauthorizedResponse(request, response, "Token validation failed",
                            "Token is expired or invalid. Please login again");
                    return;
                }

            } catch (Exception e) {
                logger.error("❌ Error during token validation for user {}: {}", email, e.getMessage());
                sendUnauthorizedResponse(request, response, "Authentication error",
                        "Please login again");
                return;
            }
        }

        // ✅ Continue with the request
        filterChain.doFilter(request, response);
    }

    /**
     * Check if the given path should be excluded from JWT validation
     * Uses startsWith to match /api/auth/login, /api/auth/login/*, etc.
     */
    private boolean isExcludedPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Remove query parameters and fragments
        String cleanPath = path.split("\\?")[0].split("#")[0];

        boolean isExcluded = EXCLUDED_PATHS.stream().anyMatch(excludedPath ->
                cleanPath.equals(excludedPath) || cleanPath.startsWith(excludedPath + "/")
        );

        if (isExcluded) {
            logger.info("✅ Path is public (excluded from JWT): {}", cleanPath);
        }

        return isExcluded;
    }

    /**
     * Send a standardized unauthorized response with proper CORS headers
     */
    private void sendUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response,
                                          String error, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Add CORS headers to error response
        String origin = request.getHeader("Origin");
        if (origin != null && isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            response.setHeader("Access-Control-Allow-Origin", "https://kalvitrack.vercel.app");
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }

        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin, X-Requested-With");

        String jsonResponse = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"success\":false,\"timestamp\":\"%s\",\"status\":401}",
                error, message, Instant.now().toString()
        );

        response.getWriter().write(jsonResponse);
        logger.debug("Sent 401 unauthorized response: {}", jsonResponse);
    }

    /**
     * Check if origin is allowed based on configured patterns
     */
    private boolean isAllowedOrigin(String origin) {
        if (origin == null) return false;

        return origin.equals("https://kalvitrack.vercel.app") ||
                origin.matches("https://.*\\.vercel\\.app") ||
                origin.matches("https://.*\\.cloudfront\\.net") ||
                origin.matches("http://localhost:[0-9]+");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        // ✅ CRITICAL: Never filter OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(method)) {
            logger.debug("✅ shouldNotFilter = true for OPTIONS (preflight) request");
            return true;
        }

        // Skip filtering for static resources and health checks
        if (requestURI.startsWith("/actuator/") ||
                requestURI.startsWith("/static/") ||
                requestURI.startsWith("/webjars/") ||
                requestURI.equals("/favicon.ico") ||
                requestURI.equals("/health")) {
            logger.debug("✅ shouldNotFilter = true for static/health resource: {}", requestURI);
            return true;
        }

        return false;
    }
}