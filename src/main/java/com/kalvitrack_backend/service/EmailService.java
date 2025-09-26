package com.kalvitrack_backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.frontend.reset-url}")
    private String frontendResetUrl; // e.g., http://localhost:5173/reset-password

    @Value("${spring.mail.username}")
    private String from;

    public void sendPasswordResetLink(String to, String token, int expiryHours) {
        String link = frontendResetUrl + "?token=" + token;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("Reset your KalviTrack password");
        msg.setText(
                "Hi,\n\nUse the link below to set your password:\n" + link +
                        "\n\nThis link expires in " + expiryHours + " hours.\n\n— KalviTrack"
        );
        mailSender.send(msg);
        logger.info("Password reset email sent to {}", to);
    }

    public void sendInvitation(String to, String token, int expiryHours) {
        String link = frontendResetUrl + "?token=" + token;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("Welcome to KalviTrack – Set your password");
        msg.setText(
                "You've been invited to KalviTrack. Click the link below to set your password:\n" + link +
                        "\n\nThe link expires in " + expiryHours + " hours."
        );
        mailSender.send(msg);
        logger.info("Invitation email sent to {}", to);
    }

    public void sendTemporaryPassword(String to, String tempPassword) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("Your KalviTrack Account – Temporary Password");
        msg.setText(
                "Hi,\n\nYour account has been created. Use the credentials below to login:\n\n" +
                        "Email: " + to + "\n" +
                        "Temporary Password: " + tempPassword + "\n\n" +
                        "You will be asked to change this password after login."
        );
        mailSender.send(msg);
        logger.info("Temporary password email sent to {}", to);
    }

    public void sendAdminForcedResetNotification(String to) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("Password Reset Required - KalviTrack");
        msg.setText(
                "Hi,\n\nYour administrator has required you to reset your password.\n\n" +
                        "You will be asked to change your password on your next login."
        );
        mailSender.send(msg);
        logger.info("Admin forced reset notification sent to {}", to);
    }
}
