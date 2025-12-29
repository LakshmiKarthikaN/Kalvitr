package com.kalvitrack_backend.controller.schedulingfeature;

import com.kalvitrack_backend.config.jwthandler.JwtUtil;
import com.kalvitrack_backend.dto.scheduling.ScheduleInterviewDTO;
import com.kalvitrack_backend.entity.InterviewSession;
import com.kalvitrack_backend.entity.Interviewer;
import com.kalvitrack_backend.repository.InterviewSessionRepository;
import com.kalvitrack_backend.repository.InterviewerRepository;
import com.kalvitrack_backend.service.schedulingfeature.InterviewSchedulingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews")
@CrossOrigin(origins = {"https://kalvitrack.vercel.app", "http://localhost:5173", "http://localhost:5173"})
public class InterviewSchedulingController {

    @Autowired
    private InterviewSchedulingService interviewSchedulingService;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private InterviewerRepository interviewerRepository;
    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    /**
     * Schedule an interview (HR only)
     */
    @PostMapping("/schedule")
    @PreAuthorize("hasRole('HR') or hasRole('ADMIN')")
    public ResponseEntity<?> scheduleInterview(
            @RequestBody ScheduleInterviewDTO dto,
            HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long hrUserId = jwtUtil.getUserIdFromToken(token);

            Map<String, Object> result = interviewSchedulingService.scheduleInterview(dto, hrUserId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Interview scheduled successfully",
                    "data", result
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to schedule interview: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/panelist/assigned-students")
    @PreAuthorize("hasAnyRole('INTERVIEW_PANELIST', 'FACULTY')")
    public ResponseEntity<?> getAssignedStudents(HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);

            // Get interviewer by user ID
            Interviewer interviewer = interviewerRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Interviewer profile not found"));

            List<Map<String, Object>> assignedStudents =
                    interviewSchedulingService.getAssignedStudentsForPanelist(interviewer.getInterviewerId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", assignedStudents,
                    "total", assignedStudents.size()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch assigned students: " + e.getMessage()
            ));
        }
    }

    /**
     * Add meeting link to interview session
     */
    @PutMapping("/panelist/add-meeting-link/{sessionId}")
    @PreAuthorize("hasAnyRole('INTERVIEW_PANELIST', 'FACULTY')")
    public ResponseEntity<?> addMeetingLink(
            @PathVariable Long sessionId,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);

            // Get interviewer by user ID
            Interviewer interviewer = interviewerRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Interviewer profile not found"));

            String meetingLink = payload.get("meetingLink");
            if (meetingLink == null || meetingLink.trim().isEmpty()) {
                throw new IllegalArgumentException("Meeting link is required");
            }

            InterviewSession session = interviewSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Interview session not found"));

            // Verify this session belongs to the current interviewer
            if (!session.getInterviewerId().equals(interviewer.getInterviewerId())) {
                throw new IllegalArgumentException("Unauthorized: This session is not assigned to you");
            }

            if (!session.getIsActive()) {
                throw new IllegalArgumentException("This interview session is not active");
            }

            session.setMeetingLink(meetingLink);
            session.setLinkAddedAt(LocalDateTime.now());
            session.setSessionStatus(InterviewSession.SessionStatus.LINK_ADDED);

            interviewSessionRepository.save(session);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Meeting link added successfully",
                    "data", Map.of(
                            "sessionId", sessionId,
                            "meetingLink", meetingLink,
                            "status", session.getSessionStatus()
                    )
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to add meeting link: " + e.getMessage()
            ));
        }
    }

    /**
     * Submit interview feedback
     */
    @PutMapping("/panelist/submit-feedback/{sessionId}")
    @PreAuthorize("hasAnyRole('INTERVIEW_PANELIST', 'FACULTY')")
    public ResponseEntity<?> submitFeedback(
            @PathVariable Long sessionId,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);

            // Get interviewer by user ID
            Interviewer interviewer = interviewerRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Interviewer profile not found"));

            String result = payload.get("result");
            String remarks = payload.get("remarks");

            if (result == null || result.trim().isEmpty()) {
                throw new IllegalArgumentException("Interview result is required");
            }

            InterviewSession session = interviewSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Interview session not found"));

            // Verify this session belongs to the current interviewer
            if (!session.getInterviewerId().equals(interviewer.getInterviewerId())) {
                throw new IllegalArgumentException("Unauthorized: This session is not assigned to you");
            }

            if (!session.getIsActive()) {
                throw new IllegalArgumentException("This interview session is not active");
            }

            // Update session with feedback
            try {
                session.setInterviewResult(InterviewSession.InterviewResult.valueOf(result));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid result value. Must be SELECTED, REJECTED, or WAITING_LIST");
            }

            session.setRemarks(remarks);
            session.setResultUpdatedAt(LocalDateTime.now());
            session.setSessionStatus(InterviewSession.SessionStatus.COMPLETED);

            interviewSessionRepository.save(session);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Feedback submitted successfully",
                    "data", Map.of(
                            "sessionId", sessionId,
                            "result", session.getInterviewResult(),
                            "status", session.getSessionStatus()
                    )
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to submit feedback: " + e.getMessage()
            ));
        }
    }
    /**
     * Get all scheduled interviews (HR only)
     */
    @GetMapping("/scheduled")
    @PreAuthorize("hasRole('HR') or hasRole('ADMIN')")
    public ResponseEntity<?> getAllScheduledInterviews() {
        try {
            List<Map<String, Object>> interviews = interviewSchedulingService.getAllScheduledInterviews();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", interviews,
                    "total", interviews.size()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch scheduled interviews: " + e.getMessage()
            ));
        }
    }

    /**
     * Cancel an interview
     */
    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('HR') or hasRole('ADMIN')")
    public ResponseEntity<?> cancelInterview(
            @PathVariable Long sessionId,
            HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long hrUserId = jwtUtil.getUserIdFromToken(token);

            interviewSchedulingService.cancelInterview(sessionId, hrUserId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Interview cancelled successfully"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to cancel interview: " + e.getMessage()
            ));
        }
    }

    /**
     * Get student's interviews
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('PMIS', 'ZSGS', 'HR', 'ADMIN')")
    public ResponseEntity<?> getStudentInterviews(@PathVariable Long studentId) {
        try {
            List<Map<String, Object>> interviews = interviewSchedulingService.getStudentInterviews(studentId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", interviews
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch student interviews: " + e.getMessage()
            ));
        }
    }

    /**
     * Get interviewer's sessions
     */
    @GetMapping("/interviewer/{interviewerId}")
    @PreAuthorize("hasAnyRole('INTERVIEW_PANELIST', 'FACULTY', 'HR', 'ADMIN')")
    public ResponseEntity<?> getInterviewerSessions(@PathVariable Long interviewerId) {
        try {
            List<Map<String, Object>> sessions = interviewSchedulingService.getInterviewerSessions(interviewerId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", sessions
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch interviewer sessions: " + e.getMessage()
            ));
        }
    }
}