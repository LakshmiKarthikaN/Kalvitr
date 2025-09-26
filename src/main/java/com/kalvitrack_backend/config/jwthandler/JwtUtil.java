package com.kalvitrack_backend.config.jwthandler;

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

    // Read from application.properties - SECURE 256-bit secret
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
                    .claim("email", email) // Add email as explicit claim too
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                    .signWith(SignatureAlgorithm.HS256, SECRET)
                    .compact();
            logger.info("Token generated successfully for {}", email);
            return token;
        } catch (Exception e) {
            logger.error("Error generating token: {}", e.getMessage());
            throw e;
        }
    }

    // Extract claims
    public Claims extractClaims(String token) {
        try {
            Claims claims = Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
            logger.debug("Successfully extracted claims from token");
            return claims;
        } catch (Exception e) {
            logger.error("Error extracting claims from token: {}", e.getMessage());
            throw new RuntimeException("Token format is invalid", e);
        }
    }

    public String extractEmail(String token) {
        try {
            String email = extractClaims(token).getSubject();
            logger.debug("Extracted email from token: {}", email);
            return email;
        } catch (Exception e) {
            logger.error("Error extracting email: {}", e.getMessage());
            throw e;
        }
    }

    // ✅ NEW: Extract role from token
    public String extractRole(String token) {
        try {
            Claims claims = extractClaims(token);
            String role = claims.get("role", String.class);
            logger.debug("Extracted role from token: {}", role);
            return role;
        } catch (Exception e) {
            logger.error("Error extracting role: {}", e.getMessage());
            throw e;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            boolean expired = extractClaims(token).getExpiration().before(new Date());
            if (expired) {
                logger.warn("Token is expired");
            }
            return expired;
        } catch (Exception e) {
            logger.error("Error checking token expiration: {}", e.getMessage());
            return true; // Consider expired if we can't check
        }
    }

    // Validate
    public boolean validateToken(String token, String email) {
        try {
            String tokenEmail = extractEmail(token);
            boolean emailMatches = email.equals(tokenEmail);
            boolean notExpired = !isTokenExpired(token);
            boolean valid = emailMatches && notExpired;

            logger.info("Token validation for {}: emailMatches={}, notExpired={}, valid={}",
                    email, emailMatches, notExpired, valid);
            return valid;
        } catch (Exception e) {
            logger.error("Token validation failed for {}: {}", email, e.getMessage());
            return false;
        }
    }

    // ✅ NEW: Method to get token info for debugging
    public void debugToken(String token) {
        try {
            Claims claims = extractClaims(token);
            logger.info("=== TOKEN DEBUG INFO ===");
            logger.info("Subject (email): {}", claims.getSubject());
            logger.info("Role: {}", claims.get("role"));
            logger.info("Issued At: {}", claims.getIssuedAt());
            logger.info("Expires At: {}", claims.getExpiration());
            logger.info("Is Expired: {}", claims.getExpiration().before(new Date()));
            logger.info("========================");
        } catch (Exception e) {
            logger.error("Error debugging token: {}", e.getMessage());
        }
    }
}