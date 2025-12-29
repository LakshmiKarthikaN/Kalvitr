package com.kalvitrack_backend.service.schedulingfeature;

import com.kalvitrack_backend.entity.InterviewSession;
import com.kalvitrack_backend.entity.Student;
import com.kalvitrack_backend.entity.Interviewer;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.repository.StudentRepository;
import com.kalvitrack_backend.repository.InterviewerRepository;
import com.kalvitrack_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.format.DateTimeFormatter;

@Service
public class SchedulingEmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private InterviewerRepository interviewerRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Send interview scheduled notification to both student and interviewer
     */
    public void sendInterviewScheduledNotification(InterviewSession session) {
        try {
            // Get student details
            Student student = studentRepository.findById(session.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // Get interviewer details
            Interviewer interviewer = interviewerRepository.findById(session.getInterviewerId())
                    .orElseThrow(() -> new RuntimeException("Interviewer not found"));

            User interviewerUser = userRepository.findById(interviewer.getUserId())
                    .orElseThrow(() -> new RuntimeException("Interviewer user not found"));

            // Send email to student
            sendEmailToStudent(student, interviewerUser, session);

            // Send email to interviewer
            sendEmailToInterviewer(interviewerUser, student, session);

            System.out.println("✅ Email notifications sent successfully for session: " + session.getSessionId());

        } catch (Exception e) {
            System.err.println("❌ Failed to send email notifications: " + e.getMessage());
            e.printStackTrace();
            // Don't throw exception - email failure shouldn't break interview scheduling
        }
    }

    /**
     * Send email to student
     */
    private void sendEmailToStudent(Student student, User interviewer, InterviewSession session)
            throws MessagingException {

        String subject = "Interview Scheduled - KalviTrack";
        String emailBody = buildStudentEmailBody(student, interviewer, session);

        sendHtmlEmail(student.getEmail(), subject, emailBody);
        System.out.println("📧 Email sent to student: " + student.getEmail());
    }

    /**
     * Send email to interviewer
     */
    private void sendEmailToInterviewer(User interviewer, Student student, InterviewSession session)
            throws MessagingException {

        String subject = "New Interview Scheduled - KalviTrack";
        String emailBody = buildInterviewerEmailBody(interviewer, student, session);

        sendHtmlEmail(interviewer.getEmail(), subject, emailBody);
        System.out.println("📧 Email sent to interviewer: " + interviewer.getEmail());
    }

    /**
     * Build email body for student
     */
    private String buildStudentEmailBody(Student student, User interviewer, InterviewSession session) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background-color: #f9f9f9; padding: 30px; border: 1px solid #ddd; }
                    .details { background-color: white; padding: 20px; margin: 20px 0; border-left: 4px solid #4CAF50; }
                    .detail-row { margin: 10px 0; }
                    .label { font-weight: bold; color: #555; }
                    .value { color: #333; }
                    .footer { background-color: #f1f1f1; padding: 15px; text-align: center; font-size: 12px; color: #666; border-radius: 0 0 5px 5px; }
                    .important { background-color: #fff3cd; padding: 15px; border-left: 4px solid #ffc107; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>🎯 Interview Scheduled</h2>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Your interview has been successfully scheduled. Please find the details below:</p>
                        
                        <div class="details">
                            <div class="detail-row">
                                <span class="label">📅 Date:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">🕐 Time:</span>
                                <span class="value">%s - %s</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">👤 Interviewer:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">📧 Interviewer Email:</span>
                                <span class="value">%s</span>
                            </div>
                        </div>
                        
                        <div class="important">
                            <strong>⚠️ Important Notes:</strong>
                            <ul>
                                <li>The meeting link will be shared by the interviewer before the scheduled time</li>
                                <li>Please be online 5 minutes before the scheduled time</li>
                                <li>Ensure you have a stable internet connection</li>
                                <li>Keep your resume and relevant documents ready</li>
                            </ul>
                        </div>
                        
                        <p>Good luck with your interview! 🍀</p>
                        <p>Best regards,<br><strong>KalviTrack Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>This is an automated email. Please do not reply to this message.</p>
                        <p>© 2025 KalviTrack. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                student.getFullName(),
                session.getInterviewDate().format(dateFormatter),
                session.getStartTime().format(timeFormatter),
                session.getEndTime().format(timeFormatter),
                interviewer.getFullName(),
                interviewer.getEmail()
        );
    }

    /**
     * Build email body for interviewer
     */
    private String buildInterviewerEmailBody(User interviewer, Student student, InterviewSession session) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #2196F3; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
                    .content { background-color: #f9f9f9; padding: 30px; border: 1px solid #ddd; }
                    .details { background-color: white; padding: 20px; margin: 20px 0; border-left: 4px solid #2196F3; }
                    .detail-row { margin: 10px 0; }
                    .label { font-weight: bold; color: #555; }
                    .value { color: #333; }
                    .footer { background-color: #f1f1f1; padding: 15px; text-align: center; font-size: 12px; color: #666; border-radius: 0 0 5px 5px; }
                    .action-required { background-color: #e3f2fd; padding: 15px; border-left: 4px solid #2196F3; margin: 20px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>📋 New Interview Assigned</h2>
                    </div>
                    <div class="content">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>A new interview has been scheduled with the following candidate:</p>
                        
                        <div class="details">
                            <div class="detail-row">
                                <span class="label">👨‍🎓 Student Name:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">📧 Student Email:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">📱 Mobile:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">🎓 College:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">📅 Date:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">🕐 Time:</span>
                                <span class="value">%s - %s</span>
                            </div>
                        </div>
                        
                        <div class="action-required">
                            <strong>📌 Action Required:</strong>
                            <ul>
                                <li>Please add the meeting link before the scheduled time</li>
                                <li>Review the candidate's profile and resume</li>
                                <li>Prepare interview questions based on the role requirements</li>
                                <li>Join the meeting 5 minutes early</li>
                            </ul>
                        </div>
                        
                        <p>Thank you for your time and commitment! 🙏</p>
                        <p>Best regards,<br><strong>KalviTrack HR Team</strong></p>
                    </div>
                    <div class="footer">
                        <p>This is an automated email. Please do not reply to this message.</p>
                        <p>© 2025 KalviTrack. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                interviewer.getFullName(),
                student.getFullName(),
                student.getEmail(),
                student.getMobileNumber() != null ? student.getMobileNumber() : "N/A",
                student.getCollegeName() != null ? student.getCollegeName() : "N/A",
                session.getInterviewDate().format(dateFormatter),
                session.getStartTime().format(timeFormatter),
                session.getEndTime().format(timeFormatter)
        );
    }

    /**
     * Send HTML email
     */
    private void sendHtmlEmail(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true); // true = HTML

        mailSender.send(message);
    }
}