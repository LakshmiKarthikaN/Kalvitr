package com.kalvitrack_backend.service.availability;

import com.kalvitrack_backend.dto.availability.InterviewerAvailabilityDTO;
import com.kalvitrack_backend.entity.Interviewer;
import com.kalvitrack_backend.entity.InterviewerAvailability;
import com.kalvitrack_backend.entity.User;
import com.kalvitrack_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class InterviewerAvailabilityService {

    @Autowired
    private InterviewerAvailabilityRepository availabilityRepository;

    @Autowired
    private InterviewerRepository interviewerRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Submit interviewer availability - stores in interviewer_availability table
     */
    public List<InterviewerAvailability> submitAvailability(Long userId, InterviewerAvailabilityDTO availabilityDTO) {
        System.out.println("=== PROCESSING AVAILABILITY SUBMISSION ===");

        // Step 1: Validate user exists and has correct role
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        System.out.println("User found: " + user.getFullName() + " (" + user.getRole() + ")");

        if (!user.getRole().equals(User.Role.INTERVIEW_PANELIST) &&
                !user.getRole().equals(User.Role.FACULTY)) {
            throw new IllegalArgumentException("Only Interview Panelists and Faculty members can set availability");
        }

        if (!user.getStatus().equals(User.Status.ACTIVE)) {
            throw new IllegalArgumentException("User account is not active");
        }

        // Step 2: Get or create interviewer record
        Interviewer interviewer = interviewerRepository.findByUserId(userId)
                .orElseGet(() -> {
                    System.out.println("Creating new interviewer record for user: " + userId);
                    Interviewer newInterviewer = new Interviewer();
                    newInterviewer.setUserId(userId);
                    newInterviewer.setMaxInterviewsPerDay(5);
                    newInterviewer.setIsActive(true);
                    return interviewerRepository.save(newInterviewer);
                });

        System.out.println("Interviewer ID: " + interviewer.getInterviewerId());

        if (!interviewer.getIsActive()) {
            throw new IllegalArgumentException("Interviewer account is inactive");
        }

        List<InterviewerAvailability> savedAvailabilities = new ArrayList<>();

        // Step 3: Process each selected date and time slots
        for (Map.Entry<String, List<InterviewerAvailabilityDTO.TimeSlot>> entry :
                availabilityDTO.getTimeSlots().entrySet()) {

            String dateStr = entry.getKey();
            LocalDate date = LocalDate.parse(dateStr);
            List<InterviewerAvailabilityDTO.TimeSlot> timeSlots = entry.getValue();

            System.out.println("Processing date: " + date + " with " + timeSlots.size() + " time slots");

            // Validate date is not in the past
            if (date.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Cannot set availability for past dates: " + date);
            }

            // Step 4: Clear existing availability for this date (replace strategy)
            System.out.println("Clearing existing availability for date: " + date);
            availabilityRepository.deleteByInterviewerIdAndAvailableDate(
                    interviewer.getInterviewerId(), date);

            // Step 5: Create new availability slots
            for (InterviewerAvailabilityDTO.TimeSlot timeSlot : timeSlots) {
                LocalTime startTime = LocalTime.parse(timeSlot.getStartTime());
                LocalTime endTime = LocalTime.parse(timeSlot.getEndTime());

                System.out.println("Creating slot: " + startTime + " - " + endTime);

                // Validate time slot
                if (!startTime.isBefore(endTime)) {
                    throw new IllegalArgumentException(
                            String.format("Invalid time slot for %s: start time (%s) must be before end time (%s)",
                                    date, startTime, endTime));
                }
                int slotDuration = availabilityDTO.getSlotDurationMinutes() != null
                        ? availabilityDTO.getSlotDurationMinutes()
                        : 60;
                List<InterviewerAvailability> slotsForBlock = splitIntoSlots(
                        interviewer.getInterviewerId(),
                        date,
                        startTime,
                        endTime,
                        slotDuration,
                        timeSlot.getNotes()
                );
                for (InterviewerAvailability slot : slotsForBlock) {
                    InterviewerAvailability saved = availabilityRepository.save(slot);
                    savedAvailabilities.add(saved);
                    System.out.println("✅ Saved slot: " + saved.getStartTime() + "-" + saved.getEndTime());
                }

                // Create and save availability record
                InterviewerAvailability availability = new InterviewerAvailability();
                availability.setInterviewerId(interviewer.getInterviewerId());
                availability.setAvailableDate(date);
                availability.setStartTime(startTime);
                availability.setEndTime(endTime);
                availability.setIsBooked(false);
                availability.setSlotDurationMinutes(60); // Default 1 hour slots
                availability.setMaxConcurrentInterviews(1);
                availability.setIsActive(true);
                availability.setNotes(timeSlot.getNotes()); // Store any notes

                InterviewerAvailability saved = availabilityRepository.save(availability);
                savedAvailabilities.add(saved);

                System.out.println("✅ Saved availability slot ID: " + saved.getAvailabilityId());
            }
        }

        System.out.println("=== TOTAL SLOTS SAVED: " + savedAvailabilities.size() + " ===");
        return savedAvailabilities;
    }

    /**
     * Get interviewer's current availability
     */
    /**
     * Get interviewer's current availability
     */
    public List<InterviewerAvailability> getInterviewerAvailability(Long userId) {
        System.out.println("=== GET INTERVIEWER AVAILABILITY ===");
        System.out.println("Fetching availability for user: " + userId);

        try {
            // Step 1: Check if user exists
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

            System.out.println("User found: " + user.getFullName() + " (Role: " + user.getRole() + ")");

            // Step 2: Check if interviewer record exists
            Optional<Interviewer> interviewerOpt = interviewerRepository.findByUserId(userId);

            if (interviewerOpt.isEmpty()) {
                System.out.println("No interviewer record found for user: " + userId);
                System.out.println("Creating new interviewer record...");

                // Create interviewer record if it doesn't exist
                Interviewer newInterviewer = new Interviewer();
                newInterviewer.setUserId(userId);
                newInterviewer.setMaxInterviewsPerDay(5);
                newInterviewer.setIsActive(true);
                Interviewer savedInterviewer = interviewerRepository.save(newInterviewer);

                System.out.println("Created new interviewer with ID: " + savedInterviewer.getInterviewerId());

                // Return empty list for new interviewer
                return new ArrayList<>();
            }

            Interviewer interviewer = interviewerOpt.get();
            System.out.println("Found interviewer ID: " + interviewer.getInterviewerId());

            if (!interviewer.getIsActive()) {
                throw new IllegalArgumentException("Interviewer account is inactive");
            }

            // Step 3: Fetch availabilities
            System.out.println("Fetching availabilities for interviewer ID: " + interviewer.getInterviewerId());

            List<InterviewerAvailability> availabilities = availabilityRepository
                    .findByInterviewerIdAndIsActiveOrderByAvailableDateAscStartTimeAsc(
                            interviewer.getInterviewerId(), true);

            System.out.println("Found " + availabilities.size() + " availability slots");

            // Log each availability for debugging
            for (InterviewerAvailability availability : availabilities) {
                System.out.println("- Slot ID " + availability.getAvailabilityId() +
                        ": " + availability.getAvailableDate() +
                        " " + availability.getStartTime() +
                        "-" + availability.getEndTime());
            }

            return availabilities;

        } catch (Exception e) {
            System.err.println("ERROR in getInterviewerAvailability: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    /**
     * Get assigned students for interviewer
     */
    public List<Map<String, Object>> getAssignedStudents(Long userId) {
        // TODO: Implement this method to fetch assigned students
        // For now, returning empty list
        return new ArrayList<>();
    }

    /**
     * Update availability slot
     */
    public InterviewerAvailability updateAvailability(Long availabilityId, Long userId, InterviewerAvailabilityDTO.TimeSlot timeSlot) {
        InterviewerAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Availability slot not found"));

        // Verify ownership
        Interviewer interviewer = interviewerRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Interviewer not found"));

        if (!availability.getInterviewerId().equals(interviewer.getInterviewerId())) {
            throw new IllegalArgumentException("You can only update your own availability slots");
        }

        if (availability.getIsBooked()) {
            throw new IllegalArgumentException("Cannot update a booked availability slot");
        }

        // Update time slot
        LocalTime startTime = LocalTime.parse(timeSlot.getStartTime());
        LocalTime endTime = LocalTime.parse(timeSlot.getEndTime());

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }

        availability.setStartTime(startTime);
        availability.setEndTime(endTime);
        availability.setNotes(timeSlot.getNotes());

        return availabilityRepository.save(availability);
    }

    /**
     * Delete availability slot
     */
    public void deleteAvailability(Long availabilityId, Long userId) {
        InterviewerAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Availability slot not found"));

        // Verify ownership
        Interviewer interviewer = interviewerRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Interviewer not found"));

        if (!availability.getInterviewerId().equals(interviewer.getInterviewerId())) {
            throw new IllegalArgumentException("You can only delete your own availability slots");
        }

        if (availability.getIsBooked()) {
            throw new IllegalArgumentException("Cannot delete a booked availability slot");
        }

        availabilityRepository.delete(availability);
    }

    /**
     * Get available slots for HR scheduling
     */
    /**
     * Get available slots for HR scheduling with duration splitting
     */
    public List<Map<String, Object>> getAvailableSlots(LocalDate startDate, LocalDate endDate, int slotDuration) {
        List<InterviewerAvailability> availableSlots = availabilityRepository
                .findAvailableSlots(startDate, endDate);

        return availableSlots.stream()
                .flatMap(slot -> splitAvailabilityIntoSlots(slot, slotDuration).stream())
                .collect(Collectors.toList());
    }
    private List<InterviewerAvailability> splitIntoSlots(
            Long interviewerId,
            LocalDate date,
            LocalTime blockStart,
            LocalTime blockEnd,
            int slotDurationMinutes,
            String notes) {

        List<InterviewerAvailability> slots = new ArrayList<>();
        LocalTime currentStart = blockStart;

        while (currentStart.plusMinutes(slotDurationMinutes).isBefore(blockEnd) ||
                currentStart.plusMinutes(slotDurationMinutes).equals(blockEnd)) {

            LocalTime currentEnd = currentStart.plusMinutes(slotDurationMinutes);

            InterviewerAvailability slot = new InterviewerAvailability();
            slot.setInterviewerId(interviewerId);
            slot.setAvailableDate(date);
            slot.setStartTime(currentStart);
            slot.setEndTime(currentEnd);
            slot.setIsBooked(false);
            slot.setSlotDurationMinutes(slotDurationMinutes);
            slot.setMaxConcurrentInterviews(1);
            slot.setIsActive(true);
            slot.setNotes(notes);

            slots.add(slot);
            currentStart = currentEnd;
        }

        return slots;
    }

    /**
     * Split a single availability block into multiple smaller slots
     */
    private List<Map<String, Object>> splitAvailabilityIntoSlots(
            InterviewerAvailability availability, int durationMinutes) {

        List<Map<String, Object>> slots = new ArrayList<>();

        LocalTime currentStart = availability.getStartTime();
        LocalTime blockEnd = availability.getEndTime();

        while (currentStart.plusMinutes(durationMinutes).isBefore(blockEnd) ||
                currentStart.plusMinutes(durationMinutes).equals(blockEnd)) {

            LocalTime slotEnd = currentStart.plusMinutes(durationMinutes);

            Map<String, Object> slotInfo = new HashMap<>();
            slotInfo.put("availabilityId", availability.getAvailabilityId());
            slotInfo.put("interviewerId", availability.getInterviewerId());
            slotInfo.put("date", availability.getAvailableDate().toString());
            slotInfo.put("startTime", currentStart.toString());
            slotInfo.put("endTime", slotEnd.toString());
            slotInfo.put("duration", durationMinutes);
            slotInfo.put("notes", availability.getNotes());

            slots.add(slotInfo);
            currentStart = slotEnd;
        }

        return slots;
    }
}