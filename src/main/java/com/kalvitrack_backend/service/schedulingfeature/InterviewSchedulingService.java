package com.kalvitrack_backend.service.schedulingfeature;

import com.kalvitrack_backend.dto.scheduling.ScheduleInterviewDTO;
import com.kalvitrack_backend.entity.InterviewSession;
import com.kalvitrack_backend.entity.InterviewerAvailability;
import com.kalvitrack_backend.entity.Student;
import com.kalvitrack_backend.entity.Interviewer;
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
public class InterviewSchedulingService {

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private InterviewerAvailabilityRepository availabilityRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private InterviewerRepository interviewerRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Schedule an interview (HR functionality)
     */
    public Map<String, Object> scheduleInterview(ScheduleInterviewDTO dto, Long hrUserId) {
        System.out.println("=== SCHEDULING INTERVIEW ===");
        System.out.println("Student ID: " + dto.getStudentId());
        System.out.println("Interviewer ID: " + dto.getInterviewerId());
        System.out.println("Date: " + dto.getDate());
        System.out.println("Time: " + dto.getStartTime() + " - " + dto.getEndTime());

        // Validate student exists and is active
        Student student = studentRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (!student.getStatus().equals(Student.StudentStatus.ACTIVE)) {
            throw new IllegalArgumentException("Student account is not active");
        }

        // Check if student already has an active interview
        if (interviewSessionRepository.existsActiveInterviewForStudent(dto.getStudentId())) {
            throw new IllegalArgumentException("Student already has an active interview scheduled");
        }

        // Validate interviewer exists and is active
        Interviewer interviewer = interviewerRepository.findById(dto.getInterviewerId())
                .orElseThrow(() -> new IllegalArgumentException("Interviewer not found"));

        if (!interviewer.getIsActive()) {
            throw new IllegalArgumentException("Interviewer is not active");
        }

        // Validate availability slot exists
        InterviewerAvailability availability = availabilityRepository.findById(dto.getAvailabilityId())
                .orElseThrow(() -> new IllegalArgumentException("Availability slot not found"));

        if (!availability.getInterviewerId().equals(dto.getInterviewerId())) {
            throw new IllegalArgumentException("Availability slot does not belong to this interviewer");
        }

        if (!availability.getIsActive()) {
            throw new IllegalArgumentException("Availability slot is not active");
        }

        LocalTime startTime = LocalTime.parse(dto.getStartTime());
        LocalTime endTime = LocalTime.parse(dto.getEndTime());

        // Validate time slot is within availability
        if (startTime.isBefore(availability.getStartTime()) || endTime.isAfter(availability.getEndTime())) {
            throw new IllegalArgumentException("Selected time is outside interviewer's availability");
        }

        // Check for overlapping sessions
        if (interviewSessionRepository.hasOverlappingSession(
                dto.getInterviewerId(), dto.getDate(), startTime, endTime)) {
            throw new IllegalArgumentException("Interviewer already has a session at this time");
        }

        // Create interview session
        InterviewSession session = new InterviewSession();
        session.setStudentId(dto.getStudentId());
        session.setInterviewerId(dto.getInterviewerId());
        session.setScheduledByHr(hrUserId);
        session.setInterviewDate(dto.getDate());
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setSessionStatus(InterviewSession.SessionStatus.SCHEDULED);
        session.setRemarks(dto.getRemarks());
        session.setIsActive(true);

        InterviewSession savedSession = interviewSessionRepository.save(session);

        System.out.println("✅ Interview scheduled successfully with ID: " + savedSession.getSessionId());

        // Get additional details for response
        User interviewerUser = userRepository.findById(interviewer.getUserId()).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", savedSession.getSessionId());
        response.put("studentName", student.getFullName());
        response.put("studentEmail", student.getEmail());
        response.put("interviewerName", interviewerUser != null ? interviewerUser.getFullName() : "Unknown");
        response.put("interviewDate", savedSession.getInterviewDate());
        response.put("startTime", savedSession.getStartTime());
        response.put("endTime", savedSession.getEndTime());
        response.put("status", savedSession.getSessionStatus());

        return response;
    }

    /**
     * Get all scheduled interviews for HR dashboard
     */
    public List<Map<String, Object>> getAllScheduledInterviews() {
        List<InterviewSession> sessions = interviewSessionRepository.findAllScheduledInterviews();

        return sessions.stream().map(session -> {
            Map<String, Object> sessionData = new HashMap<>();

            // Get student details
            Student student = studentRepository.findById(session.getStudentId()).orElse(null);
            if (student != null) {
                sessionData.put("studentId", student.getId());
                sessionData.put("studentName", student.getFullName());
                sessionData.put("studentEmail", student.getEmail());
                sessionData.put("studentMobile", student.getMobileNumber());
                sessionData.put("studentCollege", student.getCollegeName());
            }

            // Get interviewer details
            Interviewer interviewer = interviewerRepository.findById(session.getInterviewerId()).orElse(null);
            if (interviewer != null) {
                User interviewerUser = userRepository.findById(interviewer.getUserId()).orElse(null);
                if (interviewerUser != null) {
                    sessionData.put("interviewerId", interviewer.getInterviewerId());
                    sessionData.put("interviewerName", interviewerUser.getFullName());
                    sessionData.put("interviewerEmail", interviewerUser.getEmail());
                    sessionData.put("interviewerRole", interviewerUser.getRole());
                }
            }

            // Session details
            sessionData.put("sessionId", session.getSessionId());
            sessionData.put("date", session.getInterviewDate());
            sessionData.put("startTime", session.getStartTime());
            sessionData.put("endTime", session.getEndTime());
            sessionData.put("status", session.getSessionStatus());
            sessionData.put("meetingLink", session.getMeetingLink());
            sessionData.put("interviewResult", session.getInterviewResult());
            sessionData.put("remarks", session.getRemarks());
            sessionData.put("createdAt", session.getCreatedAt());

            return sessionData;
        }).collect(Collectors.toList());
    }

    /**
     * Cancel an interview
     */
    public void cancelInterview(Long sessionId, Long hrUserId) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Interview session not found"));

        if (!session.getIsActive()) {
            throw new IllegalArgumentException("Interview session is already inactive");
        }

        if (session.getSessionStatus().equals(InterviewSession.SessionStatus.COMPLETED)) {
            throw new IllegalArgumentException("Cannot cancel a completed interview");
        }

        session.setSessionStatus(InterviewSession.SessionStatus.CANCELLED);
        session.setIsActive(false);
        interviewSessionRepository.save(session);

        System.out.println("✅ Interview cancelled: Session ID " + sessionId);
    }

    /**
     * Get interviews for a specific student
     */
    public List<Map<String, Object>> getStudentInterviews(Long studentId) {
        List<InterviewSession> sessions = interviewSessionRepository
                .findByStudentIdAndIsActiveOrderByInterviewDateDesc(studentId, true);

        return sessions.stream().map(this::mapSessionToResponse).collect(Collectors.toList());
    }

    /**
     * Get interviews for a specific interviewer
     */
    public List<Map<String, Object>> getInterviewerSessions(Long interviewerId) {
        List<InterviewSession> sessions = interviewSessionRepository
                .findByInterviewerIdAndIsActiveOrderByInterviewDateDesc(interviewerId, true);

        return sessions.stream().map(this::mapSessionToResponse).collect(Collectors.toList());
    }

    private Map<String, Object> mapSessionToResponse(InterviewSession session) {
        Map<String, Object> data = new HashMap<>();

        Student student = studentRepository.findById(session.getStudentId()).orElse(null);
        if (student != null) {
            data.put("studentName", student.getFullName());
            data.put("studentEmail", student.getEmail());
        }

        Interviewer interviewer = interviewerRepository.findById(session.getInterviewerId()).orElse(null);
        if (interviewer != null) {
            User user = userRepository.findById(interviewer.getUserId()).orElse(null);
            if (user != null) {
                data.put("interviewerName", user.getFullName());
            }
        }

        data.put("sessionId", session.getSessionId());
        data.put("date", session.getInterviewDate());
        data.put("startTime", session.getStartTime());
        data.put("endTime", session.getEndTime());
        data.put("status", session.getSessionStatus());
        data.put("meetingLink", session.getMeetingLink());

        return data;
    }
}