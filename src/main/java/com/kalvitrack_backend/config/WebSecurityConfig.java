package com.kalvitrack_backend.config;

import com.kalvitrack_backend.config.jwthandler.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig {

    private final JwtFilter jwtFilter;

    public WebSecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // ✅ OPTIONS requests MUST be first
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ✅ PUBLIC ENDPOINTS - MOST SPECIFIC FIRST
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/validate-reset-token",
                                "/health",
                                "/api/health"
                        ).permitAll()

                        // ✅ STUDENT PUBLIC ENDPOINTS - BEFORE WILDCARD
                        .requestMatchers(
                                "/api/students/verify-email",
                                "/api/students/complete-registration"
                        ).permitAll()

                        // Admin endpoints
                        .requestMatchers(HttpMethod.GET, "/api/admin/users").hasRole("ADMIN")
                        .requestMatchers("/api/auth/admin/force-user-reset/**").hasRole("ADMIN")

                        // Password reset endpoints that require authentication
                        .requestMatchers("/api/auth/force-reset-password").hasAnyRole("HR", "FACULTY","INTERVIEW_PANELIST")
                        .requestMatchers("/api/auth/check-password-reset-required").authenticated()

                        // USER MANAGEMENT
                        .requestMatchers(HttpMethod.POST, "/api/users").hasAnyRole("ADMIN", "HR")
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").hasAnyRole("ADMIN", "HR")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")

                        // ROLE-SPECIFIC USER ENDPOINTS
                        .requestMatchers(HttpMethod.GET, "/api/hr/users").hasRole("HR")
                        .requestMatchers(HttpMethod.GET, "/api/users").hasAnyRole("ADMIN", "HR")

                        // PANELIST ENDPOINTS
                        .requestMatchers("/api/panelists/**").hasAnyRole("INTERVIEW_PANELIST", "HR", "ADMIN","FACULTY")

                        // INTERVIEW ENDPOINTS
                        .requestMatchers(HttpMethod.GET, "/api/interviews").hasAnyRole("HR", "ADMIN", "FACULTY")
                        .requestMatchers(HttpMethod.POST, "/api/interviews/**").hasAnyRole("HR", "ADMIN", "FACULTY")
                        .requestMatchers(HttpMethod.PUT, "/api/interviews/**").hasAnyRole("HR", "ADMIN", "FACULTY")
                        .requestMatchers(HttpMethod.DELETE, "/api/interviews/**").hasAnyRole("HR", "ADMIN")

                        // Student-specific interview endpoints
                        .requestMatchers("/api/interviews/my-interviews").hasAnyRole("PMIS", "ADMIN")
                        .requestMatchers("/api/interviews/student/**").hasAnyRole("PMIS", "HR", "ADMIN", "FACULTY")
                        .requestMatchers("/api/interviews/panelist/**").hasAnyRole("INTERVIEW_PANELIST", "HR", "ADMIN")

                        // ✅ PROTECTED STUDENT ENDPOINTS - AFTER PUBLIC ONES
                        .requestMatchers(
                                "/api/students/upload-csv",
                                "/api/students/statistics",
                                "/api/students/role/**",
                                "/api/students/incomplete"
                        ).hasAnyRole("ADMIN", "HR")

                        .requestMatchers(HttpMethod.GET, "/api/students").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/students/*/status").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/students/**").hasRole("ADMIN")
                        .requestMatchers("/api/students/export").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/students/bulk-update").hasAnyRole("HR", "ADMIN")

                        // ROLE-BASED AUTHORIZATION
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/hr/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/faculty/**").hasAnyRole("FACULTY", "ADMIN")

                        // STUDENT PROFILE ENDPOINTS
                        .requestMatchers("/api/students/profile").hasAnyRole("ZSGS", "PMIS", "STUDENT", "ADMIN", "HR")
                        .requestMatchers("/api/students/resume").hasAnyRole("ZSGS", "PMIS", "STUDENT", "ADMIN", "HR")
                        .requestMatchers("/api/students/my-**").hasAnyRole("ZSGS", "PMIS", "STUDENT", "ADMIN")

                        // PROFILE ENDPOINTS
                        .requestMatchers("/api/profile/student/**").hasAnyRole("ZSGS", "ADMIN")
                        .requestMatchers("/api/profile/**").hasAnyRole("HR", "FACULTY", "ADMIN")

                        // FILE MANAGEMENT
                        .requestMatchers("/api/files/upload").authenticated()
                        .requestMatchers("/api/files/**").authenticated()

                        // NOTIFICATIONS
                        .requestMatchers("/api/notifications/**").authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(Arrays.asList(
                "https://kalvitrack.vercel.app",
                "https://www.kalvi-track.co.in",
                "https://kalvi-track.co.in",
                "https://d1clpzx8i9nb2e.cloudfront.net"
        ));

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));

        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Total-Count"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}