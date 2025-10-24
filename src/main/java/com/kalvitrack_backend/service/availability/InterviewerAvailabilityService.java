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
     * Submit interviewer availability - stores ORIGINAL blocks only
     * Splitting happens on-demand when HR selects duration
     */
    public List<InterviewerAvailability> submitAvailability(Long userId, InterviewerAvailabilityDTO availabilityDTO) {
        System.out.println("=== PROCESSING AVAILABILITY SUBMISSION ===");

        // Validate user
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

        // Get or create interviewer record
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

        // Process each date and its time blocks
        for (Map.Entry<String, List<InterviewerAvailabilityDTO.TimeSlot>> entry :
                availabilityDTO.getTimeSlots().entrySet()) {

            String dateStr = entry.getKey();
            LocalDate date = LocalDate.parse(dateStr);
            List<InterviewerAvailabilityDTO.TimeSlot> timeSlots = entry.getValue();

            System.out.println("Processing date: " + date + " with " + timeSlots.size() + " time blocks");

            // Validate date
            if (date.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Cannot set availability for past dates: " + date);
            }

            // Clear existing availability for this date
            System.out.println("Clearing existing availability for date: " + date);
            availabilityRepository.deleteByInterviewerIdAndAvailableDate(
                    interviewer.getInterviewerId(), date);

            // Create availability records - ONE per time block (NOT split)
            for (InterviewerAvailabilityDTO.TimeSlot timeSlot : timeSlots) {
                LocalTime startTime = LocalTime.parse(timeSlot.getStartTime());
                LocalTime endTime = LocalTime.parse(timeSlot.getEndTime());

                System.out.println("Creating block: " + startTime + " - " + endTime);

                // Validate time slot
                if (!startTime.isBefore(endTime)) {
                    throw new IllegalArgumentException(
                            String.format("Invalid time slot for %s: start time (%s) must be before end time (%s)",
                                    date, startTime, endTime));
                }

                // Store DEFAULT duration (can be changed by HR later)
                int slotDuration = availabilityDTO.getSlotDurationMinutes() != null
                        ? availabilityDTO.getSlotDurationMinutes()
                        : 60;

                // Create ONE availability record for the entire block
                InterviewerAvailability availability = new InterviewerAvailability();
                availability.setInterviewerId(interviewer.getInterviewerId());
                availability.setAvailableDate(date);
                availability.setStartTime(startTime);
                availability.setEndTime(endTime);
                availability.setIsBooked(false);
                availability.setSlotDurationMinutes(slotDuration); // Default, HR can override
                availability.setMaxConcurrentInterviews(1);
                availability.setIsActive(true);
                availability.setNotes(timeSlot.getNotes());

                InterviewerAvailability saved = availabilityRepository.save(availability);
                savedAvailabilities.add(saved);

                System.out.println("✅ Saved availability block ID: " + saved.getAvailabilityId());
            }
        }

        System.out.println("=== TOTAL BLOCKS SAVED: " + savedAvailabilities.size() + " ===");
        return savedAvailabilities;
    }

    /**
     * Get interviewer's current availability (returns ORIGINAL blocks)
     */
    public List<InterviewerAvailability> getInterviewerAvailability(Long userId) {
        System.out.println("=== GET INTERVIEWER AVAILABILITY ===");
        System.out.println("Fetching availability for user: " + userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

            System.out.println("User found: " + user.getFullName() + " (Role: " + user.getRole() + ")");

            Optional<Interviewer> interviewerOpt = interviewerRepository.findByUserId(userId);

            if (interviewerOpt.isEmpty()) {
                System.out.println("No interviewer record found, creating new one...");
                Interviewer newInterviewer = new Interviewer();
                newInterviewer.setUserId(userId);
                newInterviewer.setMaxInterviewsPerDay(5);
                newInterviewer.setIsActive(true);
                Interviewer savedInterviewer = interviewerRepository.save(newInterviewer);
                System.out.println("Created new interviewer with ID: " + savedInterviewer.getInterviewerId());
                return new ArrayList<>();
            }

            Interviewer interviewer = interviewerOpt.get();
            System.out.println("Found interviewer ID: " + interviewer.getInterviewerId());

            if (!interviewer.getIsActive()) {
                throw new IllegalArgumentException("Interviewer account is inactive");
            }

            List<InterviewerAvailability> availabilities = availabilityRepository
                    .findByInterviewerIdAndIsActiveOrderByAvailableDateAscStartTimeAsc(
                            interviewer.getInterviewerId(), true);

            System.out.println("Found " + availabilities.size() + " availability blocks");

            return availabilities;

        } catch (Exception e) {
            System.err.println("ERROR in getInterviewerAvailability: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public List<Map<String, Object>> getAssignedStudents(Long userId) {
        // TODO: Implement this method to fetch assigned students
        return new ArrayList<>();
    }

    public InterviewerAvailability updateAvailability(Long availabilityId, Long userId,
                                                      InterviewerAvailabilityDTO.TimeSlot timeSlot) {
        InterviewerAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Availability slot not found"));

        Interviewer interviewer = interviewerRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Interviewer not found"));

        if (!availability.getInterviewerId().equals(interviewer.getInterviewerId())) {
            throw new IllegalArgumentException("You can only update your own availability slots");
        }

        if (availability.getIsBooked()) {
            throw new IllegalArgumentException("Cannot update a booked availability slot");
        }

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

    public void deleteAvailability(Long availabilityId, Long userId) {
        InterviewerAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Availability slot not found"));

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
}