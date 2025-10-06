package com.kalvitrack_backend.service.studentregistration;

import com.kalvitrack_backend.config.jwthandler.JwtUtil;
import com.kalvitrack_backend.dto.AdminLoginRequest;
import com.kalvitrack_backend.dto.AdminLoginResponse;
import com.kalvitrack_backend.dto.csvuploadfeature.CsvUploadResponseDto;
import com.kalvitrack_backend.dto.csvuploadfeature.StudentCsvRowDto;
import com.kalvitrack_backend.dto.emailverifyfeature.EmailVerificationResponseDto;
import com.kalvitrack_backend.dto.registration.StudentRegistrationDto;
import com.kalvitrack_backend.dto.studentspiresponse.ApiResponseDto;
import com.kalvitrack_backend.entity.Student;
import com.kalvitrack_backend.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public AdminLoginResponse loginAsStudent(AdminLoginRequest request) {
        log.info("Attempting Student table login for email: {}", request.getEmail());

        try {
            // Find student by email
            Optional<Student> studentOptional = studentRepository.findByEmail(request.getEmail());

            if (studentOptional.isEmpty()) {
                log.info("Email not found in Student table: {}", request.getEmail());
                return new AdminLoginResponse("Student not found", false);
            }

            Student student = studentOptional.get();

            // Check if student has completed registration
            if (!student.isRegistrationComplete()) {
                log.warn("Student {} has not completed registration", request.getEmail());
                return new AdminLoginResponse("Please complete your registration first", false);
            }

            // Check if student is active
            if (student.getStatus() != Student.StudentStatus.ACTIVE) {
                log.warn("Student {} has status: {}", request.getEmail(), student.getStatus());
                return new AdminLoginResponse("Account is " + student.getStatus().toString().toLowerCase(), false);
            }

            // Verify password
            if (!passwordEncoder.matches(request.getPassword(), student.getHashedPassword())) {
                log.warn("Invalid password for student: {}", request.getEmail());
                return new AdminLoginResponse("Invalid credentials", false);
            }

            // Generate JWT token with student role
            String role = student.getRole() != null ? student.getRole().name() : "STUDENT";
            String token = jwtUtil.generateToken(student.getEmail(), student.getRole().toString(),student.getId());

            log.info("Student login successful - Email: {}, Role: {}", student.getEmail(), role);

            // Use the builder pattern properly
            return AdminLoginResponse.builder()
                    .token(token)
                    .email(student.getEmail())
                    .role(role)
                    .name(student.getFullName())
                    .success(true)
                    .message("Login successful")
                    .mustResetPassword(false)
                    .userId(student.getId())
                    .status("ACTIVE")
                    .build();

        } catch (Exception e) {
            log.error("Student login error for {}: {}", request.getEmail(), e.getMessage());
            return new AdminLoginResponse("Login failed", false);
        }
    }

    @Transactional
    public CsvUploadResponseDto uploadStudentsFromCsv(MultipartFile file, String uploadedBy) {
        List<String> errors = new ArrayList<>();
        List<StudentCsvRowDto> validStudents = new ArrayList<>();

        try {
            log.info("Starting CSV upload process by: {}", uploadedBy);

            // Parse CSV file
            BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
            List<String> lines = reader.lines().collect(Collectors.toList());
            reader.close();

            if (lines.isEmpty()) {
                return CsvUploadResponseDto.failure("CSV file is empty", List.of("No data found in file"));
            }

            log.info("Processing {} lines from CSV", lines.size());

            // Skip header row and process each line
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 2) {
                    errors.add("Line " + (i + 1) + ": Invalid format - expected email,role");
                    continue;
                }

                String email = parts[0].trim();
                String role = parts[1].trim().toUpperCase();

                // Validate email format
                if (!isValidEmail(email)) {
                    errors.add("Line " + (i + 1) + ": Invalid email format - " + email);
                    continue;
                }

                // Validate role (must be ZSGS or PMIS)
                if (!isValidStudentRole(role)) {
                    errors.add("Line " + (i + 1) + ": Invalid role - " + role + ". Must be ZSGS or PMIS");
                    continue;
                }

                // Check if student already exists
                if (studentRepository.existsByEmail(email)) {
                    errors.add("Line " + (i + 1) + ": Student with email " + email + " already exists");
                    continue;
                }

                validStudents.add(new StudentCsvRowDto(email, role));
            }

            log.info("Found {} valid students to upload", validStudents.size());

            // Save valid students to database
            int successCount = 0;
            String batchId = "batch-" + System.currentTimeMillis();

            for (StudentCsvRowDto studentDto : validStudents) {
                try {
                    Student student = new Student();
                    student.setEmail(studentDto.getEmail());
                    student.setRole(Student.StudentRole.valueOf(studentDto.getRole()));
                    student.setStatus(Student.StudentStatus.ACTIVE);
                    student.setEmailVerified(false);
                    student.setFailedLoginAttempts(0);
                    student.setCreatedAt(LocalDateTime.now());
                    student.setUpdatedAt(LocalDateTime.now());

                    Student savedStudent = studentRepository.save(student);
                    log.debug("Saved student: {} with ID: {}", savedStudent.getEmail(), savedStudent.getId());
                    successCount++;

                } catch (Exception e) {
                    log.error("Error saving student: {}", studentDto.getEmail(), e);
                    errors.add("Failed to save student: " + studentDto.getEmail() + " - " + e.getMessage());
                }
            }

            log.info("Successfully uploaded {} out of {} students", successCount, validStudents.size());

            if (successCount > 0) {
                CsvUploadResponseDto response = CsvUploadResponseDto.success(batchId, validStudents.size(), successCount);
                if (!errors.isEmpty()) {
                    response.setErrors(errors);
                }
                return response;
            } else {
                return CsvUploadResponseDto.failure("No students were uploaded", errors);
            }

        } catch (Exception e) {
            log.error("Error processing CSV file", e);
            return CsvUploadResponseDto.failure("Error processing CSV file: " + e.getMessage(),
                    List.of("Please check file format and try again"));
        }
    }

    public EmailVerificationResponseDto verifyEmail(String email) {
        log.info("Verifying email for registration: {}", email);

        Optional<Student> studentOpt = studentRepository.findByEmail(email);

        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            boolean isComplete = student.isRegistrationComplete();

            log.info("Email {} found in system. Registration complete: {}", email, isComplete);
            return EmailVerificationResponseDto.exists(student.getRole().name(), isComplete);
        }

        log.info("Email {} not found in system", email);
        return EmailVerificationResponseDto.notExists();
    }

    @Transactional
    public ApiResponseDto<String> completeRegistration(StudentRegistrationDto registrationDto) {
        try {
            log.info("=== STARTING REGISTRATION COMPLETION ===");
            log.info("Attempting to complete registration for: {}", registrationDto.getEmail());
            log.info("Registration DTO: {}", registrationDto);

            // Find student by email
            Optional<Student> studentOpt = studentRepository.findByEmail(registrationDto.getEmail());

            if (studentOpt.isEmpty()) {
                log.warn("Registration attempted for non-existent email: {}", registrationDto.getEmail());
                return ApiResponseDto.error("Email not found. Please contact HR department.");
            }

            Student student = studentOpt.get();
            log.info("Found student: ID={}, Email={}, Current Status={}",
                    student.getId(), student.getEmail(), student.getStatus());

            // Check if registration is already complete
            if (student.isRegistrationComplete()) {
                log.warn("Registration already completed for: {}", registrationDto.getEmail());
                return ApiResponseDto.error("Registration already completed for this email.");
            }

            // Update student details
            log.info("Updating student details...");
            student.setFullName(registrationDto.getFullName());
            student.setHashedPassword(passwordEncoder.encode(registrationDto.getPassword()));
            student.setMobileNumber(registrationDto.getMobileNumber());
            student.setCollegeName(registrationDto.getCollegeName());
            student.setYearOfGraduation(Year.of(registrationDto.getYearOfGraduation()));

            if (registrationDto.getResumePath() != null) {
                student.setResumePath(registrationDto.getResumePath());
                log.info("Resume path set: {}", registrationDto.getResumePath());
            }

            student.setEmailVerified(true);
            student.setUpdatedAt(LocalDateTime.now());

            // CRITICAL: Explicitly set registration as complete
            // You need to add this method to your Student entity if it doesn't exist
            student.setRegistrationComplete(true);

            log.info("About to save student with details:");
            log.info("  - Full Name: {}", student.getFullName());
            log.info("  - Mobile: {}", student.getMobileNumber());
            log.info("  - College: {}", student.getCollegeName());
            log.info("  - Graduation Year: {}", student.getYearOfGraduation());
            log.info("  - Registration Complete: {}", student.isRegistrationComplete());

            // Save to database
            Student savedStudent = studentRepository.save(student);

            log.info("=== REGISTRATION SAVED SUCCESSFULLY ===");
            log.info("Saved student ID: {}", savedStudent.getId());
            log.info("Registration completed successfully for student: {} with role: {}",
                    registrationDto.getEmail(), savedStudent.getRole().name());

            // Verify the save worked by fetching from database
            Optional<Student> verifyStudent = studentRepository.findByEmail(savedStudent.getEmail());
            if (verifyStudent.isPresent() && verifyStudent.get().isRegistrationComplete()) {
                log.info("Verification successful - student registration is complete in database");
            } else {
                log.error("Verification failed - student may not have been saved correctly");
                return ApiResponseDto.error("Registration save verification failed");
            }

            return ApiResponseDto.success("Registration completed successfully!", savedStudent.getRole().name());

        } catch (Exception e) {
            log.error("=== REGISTRATION ERROR ===");
            log.error("Error completing registration for: {}", registrationDto.getEmail(), e);
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Exception message: {}", e.getMessage());

            // Print full stack trace for debugging
            e.printStackTrace();

            return ApiResponseDto.error("Registration failed. Please try again.");
        }
    }

    public List<Student> getAllStudents() {
        try {
            List<Student> students = studentRepository.findAll();
            log.info("Retrieved {} total students", students.size());
            return students;
        } catch (Exception e) {
            log.error("Error fetching all students", e);
            return new ArrayList<>();
        }
    }

    public List<Student> getStudentsByRole(Student.StudentRole role) {
        try {
            List<Student> students = studentRepository.findByRole(role);
            log.info("Retrieved {} students with role: {}", students.size(), role);
            return students;
        } catch (Exception e) {
            log.error("Error fetching students by role: {}", role, e);
            return new ArrayList<>();
        }
    }

    public List<Student> getIncompleteRegistrations() {
        try {
            List<Student> incomplete = studentRepository.findIncompleteRegistrations();
            log.info("Retrieved {} incomplete registrations", incomplete.size());
            return incomplete;
        } catch (Exception e) {
            log.error("Error fetching incomplete registrations", e);
            return new ArrayList<>();
        }
    }

    public List<Student> getCompleteRegistrations() {
        try {
            List<Student> complete = studentRepository.findCompleteRegistrations();
            log.info("Retrieved {} complete registrations", complete.size());
            return complete;
        } catch (Exception e) {
            log.error("Error fetching complete registrations", e);
            return new ArrayList<>();
        }
    }

    public Map<String, Long> getStudentStatistics() {
        try {
            Map<String, Long> stats = new HashMap<>();
            stats.put("total", studentRepository.count());
            stats.put("zsgs", studentRepository.countByRole(Student.StudentRole.ZSGS));
            stats.put("pmis", studentRepository.countByRole(Student.StudentRole.PMIS));
            stats.put("active", studentRepository.countByStatus(Student.StudentStatus.ACTIVE));
            stats.put("pending", studentRepository.countByStatus(Student.StudentStatus.PENDING));

            List<Student> incomplete = studentRepository.findIncompleteRegistrations();
            List<Student> complete = studentRepository.findCompleteRegistrations();
            stats.put("incomplete", (long) incomplete.size());
            stats.put("complete", (long) complete.size());

            log.info("Generated student statistics: {}", stats);
            return stats;
        } catch (Exception e) {
            log.error("Error generating student statistics", e);
            return new HashMap<>();
        }
    }

    private boolean isValidEmail(String email) {
        return email != null &&
                email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") &&
                email.length() <= 255;
    }

    private boolean isValidStudentRole(String role) {
        try {
            Student.StudentRole.valueOf(role.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}