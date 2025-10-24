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

@RestController
@RequestMapping("/api/panelists")
@CrossOrigin(origins = {"https://kalvitrack.vercel.app", "http://localhost:5173", "http://localhost:5174"})
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
     */
    @PostMapping("/availability")
    @PreAuthorize("hasRole('INTERVIEW_PANELIST') or hasRole('FACULTY')")
    public ResponseEntity<?> submitAvailability(
            @RequestBody InterviewerAvailabilityDTO availabilityDTO,
            HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);
            String userRole = jwtUtil.getRoleFromToken(token);

            if (!userRole.equals("INTERVIEW_PANELIST") && !userRole.equals("FACULTY")) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Only Interview Panelists and Faculty members can set availability"
                ));
            }

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
     * Get interviewer's own availability (shows ORIGINAL blocks, not split)
     */
    @GetMapping("/availability")
    @PreAuthorize("hasRole('INTERVIEW_PANELIST') or hasRole('FACULTY')")
    public ResponseEntity<?> getMyAvailability(HttpServletRequest request) {
        try {
            String token = jwtUtil.getTokenFromRequest(request);
            Long userId = jwtUtil.getUserIdFromToken(token);

            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "success", false,
                        "message", "Token doesn't contain userId. Please log in again."
                ));
            }

            // Get interviewer
            Interviewer interviewer = interviewerRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Interviewer not found"));

            // Get only ORIGINAL blocks (where start_time and slot_duration_minutes indicate original)
            List<InterviewerAvailability> availabilities = availabilityRepository
                    .findByInterviewerIdAndIsActiveOrderByAvailableDateAscStartTimeAsc(
                            interviewer.getInterviewerId(), true);

            // Group by date and time range to show original blocks
            Map<String, InterviewerAvailability> uniqueBlocks = new HashMap<>();

            for (InterviewerAvailability avail : availabilities) {
                String key = avail.getAvailableDate() + "_" + avail.getStartTime();
                if (!uniqueBlocks.containsKey(key)) {
                    uniqueBlocks.put(key, avail);
                } else {
                    // Keep the one with latest end time (original block)
                    InterviewerAvailability existing = uniqueBlocks.get(key);
                    if (avail.getEndTime().isAfter(existing.getEndTime())) {
                        uniqueBlocks.put(key, avail);
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", new ArrayList<>(uniqueBlocks.values())
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
     * Get available slots for HR scheduling - SPLITS into selected duration
     */
    @GetMapping("/available-slots")
    @PreAuthorize("hasRole('HR') or hasRole('ADMIN')")
    public ResponseEntity<?> getAvailableSlots(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "60") int slotDuration) {

        System.out.println("=== GET AVAILABLE SLOTS FOR HR ===");
        System.out.println("Slot Duration: " + slotDuration + " minutes");

        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusMonths(1);

        // Get all available slots (original blocks)
        List<InterviewerAvailability> availableSlots = availabilityRepository
                .findAvailableSlots(startDate, endDate);

        System.out.println("Found " + availableSlots.size() + " availability blocks");

        // Group by interviewer and date, then split
        Map<String, List<InterviewerAvailability>> groupedSlots = availableSlots.stream()
                .collect(Collectors.groupingBy(slot ->
                        slot.getInterviewerId() + "_" + slot.getAvailableDate().toString()
                ));

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map.Entry<String, List<InterviewerAvailability>> entry : groupedSlots.entrySet()) {
            List<InterviewerAvailability> daySlots = entry.getValue();
            if (daySlots.isEmpty()) continue;

            InterviewerAvailability firstSlot = daySlots.get(0);

            // Get interviewer details
            Interviewer interviewer = interviewerRepository.findById(firstSlot.getInterviewerId())
                    .orElse(null);
            if (interviewer == null) continue;

            User user = userRepository.findById(interviewer.getUserId())
                    .orElse(null);
            if (user == null) continue;

            // Split the blocks into slots of selected duration
            List<Map<String, String>> splitSlots = new ArrayList<>();

            for (InterviewerAvailability block : daySlots) {
                List<Map<String, String>> blockSlots = splitTimeBlock(
                        block.getStartTime(),
                        block.getEndTime(),
                        slotDuration,
                        block.getAvailabilityId()
                );
                splitSlots.addAll(blockSlots);
            }

            if (splitSlots.isEmpty()) continue;

            Map<String, Object> slotInfo = new HashMap<>();
            slotInfo.put("interviewerId", firstSlot.getInterviewerId());
            slotInfo.put("userId", interviewer.getUserId()); // ✅ This is what frontend expects
            slotInfo.put("interviewerName", user.getFullName());
            slotInfo.put("interviewerEmail", user.getEmail());
            slotInfo.put("interviewerRole", user.getRole().toString());
            slotInfo.put("date", firstSlot.getAvailableDate().toString());
            slotInfo.put("totalSlots", splitSlots.size());
            slotInfo.put("slots", splitSlots);
            slotInfo.put("notes", firstSlot.getNotes());

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

            results.add(slotInfo);
        }

        System.out.println("=== RETURNING " + results.size() + " GROUPED SLOTS ===");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", results,
                "totalInterviewers", results.size()
        ));
    }

    /**
     * Helper: Split time block into slots
     */
    private List<Map<String, String>> splitTimeBlock(
            LocalTime startTime,
            LocalTime endTime,
            int durationMinutes,
            Long availabilityId) {

        List<Map<String, String>> slots = new ArrayList<>();
        LocalTime currentStart = startTime;

        while (currentStart.plusMinutes(durationMinutes).isBefore(endTime) ||
                currentStart.plusMinutes(durationMinutes).equals(endTime)) {

            LocalTime slotEnd = currentStart.plusMinutes(durationMinutes);

            Map<String, String> slot = new HashMap<>();
            slot.put("availabilityId", availabilityId.toString());
            slot.put("startTime", currentStart.toString());
            slot.put("endTime", slotEnd.toString());
            slot.put("duration", String.valueOf(durationMinutes));

            slots.add(slot);
            currentStart = slotEnd;
        }

        return slots;
    }

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
}