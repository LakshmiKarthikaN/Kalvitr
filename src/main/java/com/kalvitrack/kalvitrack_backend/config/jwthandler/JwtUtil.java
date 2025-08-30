package com.kalvitrack.kalvitrack_backend.config.jwthandler;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    // ✅ Read from application.properties - SECURE 256-bit secret
    @Value("${jwt.secret:mySecretKeyForKalviTrackAppThatIsAtLeast32CharactersLongForHS256Algorithm}")
    private String SECRET;

    @Value("${jwt.expiration:3600000}")
    private long EXPIRATION;

    // Generate Token
    public String generateToken(String email, String role) {
        logger.info("Generating token for email: {} with role: {}", email, role);
        try {
            String token = Jwts.builder()
                    .setSubject(email)
                    .claim("role", role)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                    .signWith(SignatureAlgorithm.HS256, SECRET)
                    .compact();
            logger.info("Token generated successfully");
            return token;
        } catch (Exception e) {
            logger.error("Error generating token: {}", e.getMessage());
            throw e;
        }
    }

    // Extract claims
    public Claims extractClaims(String token) {
        try {
            return Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
        } catch (Exception e) {
            logger.error("Error extracting claims from token: {}", e.getMessage());
            throw e;
        }
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenExpired(String token) {
        boolean expired = extractClaims(token).getExpiration().before(new Date());
        if (expired) {
            logger.warn("Token is expired");
        }
        return expired;
    }

    // Validate
    public boolean validateToken(String token, String email) {
        try {
            String tokenEmail = extractEmail(token);
            boolean valid = email.equals(tokenEmail) && !isTokenExpired(token);
            logger.info("Token validation for {}: {}", email, valid ? "SUCCESS" : "FAILED");
            return valid;
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
}