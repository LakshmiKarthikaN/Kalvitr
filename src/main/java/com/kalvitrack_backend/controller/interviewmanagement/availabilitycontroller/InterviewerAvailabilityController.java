package com.kalvitrack_backend.controller.interviewmanagement.availabilitycontroller;

import com.kalvitrack_backend.config.jwthandler.JwtUtil;
import com.kalvitrack_backend.dto.availability.InterviewerAvailabilityDTO;
import com.kalvitrack_backend.entity.Interviewer;
import com.kalvitrack_backend.entity.InterviewerAvailability;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.repository.InterviewerAvailabilityRepository;
import com.kalvitrack_backend.repository.InterviewerRepository;
import com.kalvitrack_backend.repository.UserRepository;
import com.kalvitrack_backend.service.availability.InterviewerAvailabilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/panelists")
@CrossOrigin(
        origins = {
                "https://kalvitrack.vercel.app",
                "https://kalvi-track.co.in",
                "https://www.kalvi-track.co.in",
                "http://localhost:3000",
                "http://localhost:5173"
        },
        allowedHeaders = "*",
        allowCredentials = "true",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}
)
public class InterviewerAvailabilityController {

    @Autowired
    private InterviewerAvailabilityService availabilityService;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private InterviewerAvailabilityRepository availabilityRepository;

    @Autowired
    private InterviewerRepository interviewerRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Submit interviewer availability
     * Only INTERVIEW_PANELIST and FACULTY roles can set their availability
     */
    @PostMapping("/availability")
    @PreAuthorize("hasRole('INTERVIEW_PANELIST') or hasRole('FACULTY')")
    public ResponseEntity<?> submitAvailability(
            @RequestBody InterviewerAvailabilityDTO availabilityDTO,
            HttpServletRequest request) {
        try {
            // Extract user ID from JWT token
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);
            String userRole = jwtUtil.getRoleFromToken(token);

            // Validate user role
            if (!userRole.equals("INTERVIEW_PANELIST") && !userRole.equals("FACULTY")) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Only Interview Panelists and Faculty members can set availability"
                ));
            }

            // Submit availability
            List<InterviewerAvailability> savedAvailabilities =
                    availabilityService.submitAvailability(userId, availabilityDTO);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Availability submitted successfully",
                    "data", savedAvailabilities,
                    "totalSlots", savedAvailabilities.size()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to submit availability: " + e.getMessage()
            ));
        }
    }

    /**
     * Get interviewer's own availability
     */
    @GetMapping("/availability")
    public ResponseEntity<?> getMyAvailability(HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            System.out.println("🔍 Token exists: " + (token != null));

            if (token != null) {
                jwtUtil.debugToken(token); // This will print all token claims
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            System.out.println("🔍 Extracted userId: " + userId);

            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "success", false,
                        "message", "Token doesn't contain userId. Please log in again."
                ));
            }

            List<InterviewerAvailability> availabilities =
                    availabilityService.getInterviewerAvailability(userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", availabilities
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Get assigned students for interviewer
     * Only INTERVIEW_PANELIST and FACULTY can see their assigned students
     */
    @GetMapping("/assigned-students")
    @PreAuthorize("hasRole('INTERVIEW_PANELIST') or hasRole('FACULTY')")
    public ResponseEntity<?> getAssignedStudents(HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);

            List<Map<String, Object>> assignedStudents =
                    availabilityService.getAssignedStudents(userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", assignedStudents
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch assigned students: " + e.getMessage()
            ));
        }
    }

    /**
     * Update availability slot
     */
    @PutMapping("/availability/{availabilityId}")
    @PreAuthorize("hasRole('INTERVIEW_PANELIST') or hasRole('FACULTY')")
    public ResponseEntity<?> updateAvailability(
            @PathVariable Long availabilityId,
            @RequestBody InterviewerAvailabilityDTO.TimeSlot timeSlot,
            HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);

            InterviewerAvailability updated = availabilityService.updateAvailability(
                    availabilityId, userId, timeSlot);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Availability updated successfully",
                    "data", updated
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to update availability: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete availability slot
     */
    @DeleteMapping("/availability/{availabilityId}")
    @PreAuthorize("hasRole('INTERVIEW_PANELIST') or hasRole('FACULTY')")
    public ResponseEntity<?> deleteAvailability(
            @PathVariable Long availabilityId,
            HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);

            availabilityService.deleteAvailability(availabilityId, userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Availability deleted successfully"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to delete availability: " + e.getMessage()
            ));
        }
    }

    /**
     * Get available interview slots for HR scheduling
     * Only HR and ADMIN can access this
     */
    @GetMapping("/available-slots")
    @PreAuthorize("hasRole('HR') or hasRole('ADMIN')")
    public ResponseEntity<?> getAvailableSlots(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {

        System.out.println("=== GET AVAILABLE SLOTS ===");

        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusMonths(1);

        System.out.println("Start Date: " + startDate);
        System.out.println("End Date: " + endDate);

        List<InterviewerAvailability> availableSlots = availabilityRepository
                .findAvailableSlots(startDate, endDate);

        System.out.println("Found " + availableSlots.size() + " slots from repository");

        // Group slots by interviewer and date
        Map<String, List<InterviewerAvailability>> groupedSlots = availableSlots.stream()
                .collect(Collectors.groupingBy(slot ->
                        slot.getInterviewerId() + "_" + slot.getAvailableDate().toString()
                ));

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map.Entry<String, List<InterviewerAvailability>> entry : groupedSlots.entrySet()) {
            List<InterviewerAvailability> daySlots = entry.getValue();
            if (daySlots.isEmpty()) continue;

            InterviewerAvailability firstSlot = daySlots.get(0);

            Interviewer interviewer = interviewerRepository.findById(firstSlot.getInterviewerId())
                    .orElse(null);
            if (interviewer == null) continue;

            User user = userRepository.findById(interviewer.getUserId())
                    .orElse(null);
            if (user == null) continue;

            Map<String, Object> slotInfo = new HashMap<>();
            slotInfo.put("interviewerId", firstSlot.getInterviewerId());
            slotInfo.put("userId", interviewer.getUserId());
            slotInfo.put("interviewerName", user.getFullName());
            slotInfo.put("interviewerEmail", user.getEmail());
            slotInfo.put("interviewerRole", user.getRole().toString());
            slotInfo.put("date", firstSlot.getAvailableDate().toString());
            slotInfo.put("totalSlots", daySlots.size());

            // Include individual slots for detailed view
            List<Map<String, String>> slotDetails = daySlots.stream()
                    .map(slot -> {
                        Map<String, String> detail = new HashMap<>();
                        detail.put("availabilityId", slot.getAvailabilityId().toString());
                        detail.put("startTime", slot.getStartTime().toString());
                        detail.put("endTime", slot.getEndTime().toString());
                        detail.put("duration", String.valueOf(slot.getSlotDurationMinutes()));
                        return detail;
                    })
                    .collect(Collectors.toList());

            slotInfo.put("slots", slotDetails);

            // Time range summary
            LocalTime earliestStart = daySlots.stream()
                    .map(InterviewerAvailability::getStartTime)
                    .min(LocalTime::compareTo)
                    .orElse(null);
            LocalTime latestEnd = daySlots.stream()
                    .map(InterviewerAvailability::getEndTime)
                    .max(LocalTime::compareTo)
                    .orElse(null);

            slotInfo.put("timeRange", earliestStart + " - " + latestEnd);
            slotInfo.put("notes", firstSlot.getNotes());

            results.add(slotInfo);
        }

        System.out.println("=== RETURNING " + results.size() + " GROUPED SLOTS ===");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", results,
                "totalSlots", availableSlots.size()
        ));
    }

    /**
     * Split an availability block into smaller time slots
     */
    private List<Map<String, Object>> splitIntoSlots(
            InterviewerAvailability availability,
            Interviewer interviewer,
            User user,
            int durationMinutes) {

        List<Map<String, Object>> slots = new ArrayList<>();

        LocalTime currentStart = availability.getStartTime();
        LocalTime blockEnd = availability.getEndTime();

        while (currentStart.plusMinutes(durationMinutes).isBefore(blockEnd) ||
                currentStart.plusMinutes(durationMinutes).equals(blockEnd)) {

            LocalTime slotEnd = currentStart.plusMinutes(durationMinutes);

            Map<String, Object> slotInfo = new HashMap<>();
            slotInfo.put("availabilityId", availability.getAvailabilityId());
            slotInfo.put("interviewerId", availability.getInterviewerId());
            slotInfo.put("userId", interviewer.getUserId());
            slotInfo.put("interviewerName", user.getFullName());
            slotInfo.put("interviewerEmail", user.getEmail());
            slotInfo.put("interviewerRole", user.getRole().toString());
            slotInfo.put("date", availability.getAvailableDate().toString());
            slotInfo.put("startTime", currentStart.toString());
            slotInfo.put("endTime", slotEnd.toString());
            slotInfo.put("duration", durationMinutes);
            slotInfo.put("notes", availability.getNotes());
            slotInfo.put("originalBlockStart", availability.getStartTime().toString());
            slotInfo.put("originalBlockEnd", availability.getEndTime().toString());

            slots.add(slotInfo);
            currentStart = slotEnd;
        }

        return slots;
    }
}