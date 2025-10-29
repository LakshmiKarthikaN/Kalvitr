package com.kalvitrack_backend.controller.schedulingfeature;

import com.kalvitrack_backend.config.jwthandler.JwtUtil;
import com.kalvitrack_backend.dto.scheduling.ScheduleInterviewDTO;
import com.kalvitrack_backend.service.schedulingfeature.InterviewSchedulingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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