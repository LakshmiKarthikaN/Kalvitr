package com.kalvitrack_backend.controller.registrationfeature;

import com.kalvitrack_backend.dto.csvuploadfeature.CsvUploadResponseDto;
import com.kalvitrack_backend.dto.emailverifyfeature.EmailVerificationDto;
import com.kalvitrack_backend.dto.emailverifyfeature.EmailVerificationResponseDto;
import com.kalvitrack_backend.dto.registration.StudentRegistrationDto;
import com.kalvitrack_backend.dto.studentspiresponse.ApiResponseDto;
import com.kalvitrack_backend.entity.Student;
import com.kalvitrack_backend.repository.StudentRepository;
import com.kalvitrack_backend.service.studentregistration.AuthService;
import com.kalvitrack_backend.service.studentregistration.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins ={ "https://kalvitrack.vercel.app", "http://localhost:5173",
        "http://localhost:5174"})
public class StudentController {

    private final StudentService studentService;
    private final AuthService authService;
    private final StudentRepository studentRepository;
    /**
     * Upload students from CSV - Only HR and ADMIN can access
     */
    @PostMapping("/upload-csv")
    public ResponseEntity<CsvUploadResponseDto> uploadStudentsFromCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadedBy") String uploadedBy) {

        try {
            log.info("=== CSV Upload Endpoint Hit ===");
            log.info("CSV upload request from: {}, file: {}", uploadedBy, file.getOriginalFilename());
            log.info("Current user: {}", authService.getCurrentUsername());
            log.info("Current user role: {}", authService.getCurrentUserRole());
            log.info("Is authenticated: {}", authService.isAuthenticated());
            log.info("Can manage students: {}", authService.canManageStudents());

            // Check if user is authenticated
            if (!authService.isAuthenticated()) {
                log.warn("Unauthenticated access attempt to upload CSV");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(CsvUploadResponseDto.failure("Authentication required",
                                List.of("Please login to upload student data")));
            }

            // Check authorization - only HR and ADMIN can upload
            if (!authService.canManageStudents()) {
                log.warn("Unauthorized access attempt to upload CSV by user: {} with role: {}",
                        authService.getCurrentUsername(), authService.getCurrentUserRole());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(CsvUploadResponseDto.failure("Access denied",
                                List.of("You don't have permission to upload student data")));
            }

            // Validate file
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(CsvUploadResponseDto.failure("File is empty",
                                List.of("Please select a valid CSV file")));
            }

            if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                return ResponseEntity.badRequest()
                        .body(CsvUploadResponseDto.failure("Invalid file type",
                                List.of("Please upload a CSV file")));
            }

            // Check file size (max 5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(CsvUploadResponseDto.failure("File too large",
                                List.of("File size must be less than 5MB")));
            }

            log.info("File validation passed, processing CSV...");
            CsvUploadResponseDto response = studentService.uploadStudentsFromCsv(file, uploadedBy);

            if (response.isSuccess()) {
                log.info("CSV upload completed successfully: {} students uploaded",
                        response.getSuccessfulRecords());
                return ResponseEntity.ok(response);
            } else {
                log.warn("CSV upload failed: {}", response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error uploading CSV", e);
            return ResponseEntity.internalServerError()
                    .body(CsvUploadResponseDto.failure("Server error occurred",
                            List.of("Please try again later: " + e.getMessage())));
        }
    }
    // Add this to your StudentController.java

    /**
     * Create student manually - Only HR and ADMIN can access
     */
    @PostMapping("/create-manual")
    public ResponseEntity<ApiResponseDto<String>> createStudentManually(
            @RequestBody Map<String, String> request) {

        try {
            log.info("=== Manual Student Creation Request ===");
            log.info("Current user: {}", authService.getCurrentUsername());
            log.info("Request data: {}", request);

            // Check authentication
            if (!authService.isAuthenticated()) {
                log.warn("Unauthenticated attempt to create student");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponseDto.error("Authentication required"));
            }

            // Check authorization - only HR and ADMIN can create students manually
            if (!authService.canManageStudents()) {
                log.warn("Unauthorized attempt to create student by user: {} with role: {}",
                        authService.getCurrentUsername(), authService.getCurrentUserRole());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponseDto.error("You don't have permission to create students"));
            }

            // Extract and validate email
            String email = request.get("email");
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponseDto.error("Email is required"));
            }
            email = email.trim().toLowerCase();

            // Validate email format
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                return ResponseEntity.badRequest()
                        .body(ApiResponseDto.error("Invalid email format"));
            }

            // Extract and validate role
            String roleStr = request.get("role");
            if (roleStr == null || roleStr.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponseDto.error("Role is required"));
            }

            Student.StudentRole role;
            try {
                role = Student.StudentRole.valueOf(roleStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponseDto.error("Invalid role. Must be ZSGS or PMIS"));
            }

            // Check if student already exists
            if (studentRepository.existsByEmail(email)) {
                log.warn("Attempt to create duplicate student: {}", email);
                return ResponseEntity.badRequest()
                        .body(ApiResponseDto.error("A student with this email already exists"));
            }

            // Create new student
            Student student = new Student();
            student.setEmail(email);
            student.setRole(role);
            student.setStatus(Student.StudentStatus.ACTIVE);
            student.setEmailVerified(false);
            student.setFailedLoginAttempts(0);
            student.setCreatedAt(LocalDateTime.now());
            student.setUpdatedAt(LocalDateTime.now());

            // Save to database
            Student savedStudent = studentRepository.save(student);

            log.info("Successfully created student manually: {} with ID: {} and role: {}",
                    savedStudent.getEmail(), savedStudent.getId(), savedStudent.getRole());

            return ResponseEntity.ok()
                    .body(ApiResponseDto.success(
                            "Student created successfully! Registration email sent.",
                            savedStudent.getEmail()
                    ));

        } catch (Exception e) {
            log.error("Error creating student manually", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to create student: " + e.getMessage()));
        }
    }

    /**
     * Verify email exists in system (for registration) - Public endpoint
     */
    @PostMapping("/verify-email")
    public ResponseEntity<EmailVerificationResponseDto> verifyEmail(
            @Valid @RequestBody EmailVerificationDto request) {

        try {
            log.info("Email verification request for: {}", request.getEmail());

            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new EmailVerificationResponseDto(false, null, false, "Email is required"));
            }

            EmailVerificationResponseDto response = studentService.verifyEmail(request.getEmail().trim());

            if (response.isExists()) {
                log.info("Email {} verified successfully, registration complete: {}",
                        request.getEmail(), response.isRegistrationComplete());
                return ResponseEntity.ok(response);
            } else {
                log.info("Email {} not found in system", request.getEmail());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error verifying email: {}", request.getEmail(), e);
            return ResponseEntity.internalServerError()
                    .body(new EmailVerificationResponseDto(false, null, false, "Server error occurred"));
        }
    }

    /**
     * Complete student registration - Public endpoint (UPDATED to handle multipart form data)
     */
    @PostMapping(value = "/complete-registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDto<String>> completeRegistration(
            @RequestParam("email") String email,
            @RequestParam("fullName") String fullName,
            @RequestParam("password") String password,
            @RequestParam("mobileNumber") String mobileNumber,
            @RequestParam("collegeName") String collegeName,
            @RequestParam("yearOfGraduation") Integer yearOfGraduation,
            @RequestParam(value = "resume", required = false) MultipartFile resume) {

        try {
            log.info("=== COMPLETE REGISTRATION REQUEST ===");
            log.info("Email: {}", email);
            log.info("Full Name: {}", fullName);
            log.info("Mobile: {}", mobileNumber);
            log.info("College: {}", collegeName);
            log.info("Graduation Year: {}", yearOfGraduation);
            log.info("Resume provided: {}", resume != null ? resume.getOriginalFilename() : "No");

            // Validate required fields
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponseDto.error("Email is required")
                );
            }

            if (fullName == null || fullName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponseDto.error("Full name is required")
                );
            }

            if (password == null || password.length() < 6) {
                return ResponseEntity.badRequest().body(
                        ApiResponseDto.error("Password must be at least 6 characters long")
                );
            }

            if (mobileNumber == null || !mobileNumber.matches("^[0-9]{10}$")) {
                return ResponseEntity.badRequest().body(
                        ApiResponseDto.error("Mobile number must be 10 digits")
                );
            }

            if (collegeName == null || collegeName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponseDto.error("College name is required")
                );
            }

            if (yearOfGraduation == null || yearOfGraduation < 1990 || yearOfGraduation > 2030) {
                return ResponseEntity.badRequest().body(
                        ApiResponseDto.error("Invalid graduation year")
                );
            }

            // Handle resume upload if provided
            String resumePath = null;
            if (resume != null && !resume.isEmpty()) {
                try {
                    // Validate file type
                    String contentType = resume.getContentType();
                    if (contentType == null || (!contentType.equals("application/pdf") &&
                            !contentType.equals("application/msword") &&
                            !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
                        return ResponseEntity.badRequest().body(
                                ApiResponseDto.error("Resume must be PDF, DOC, or DOCX format")
                        );
                    }

                    // Validate file size (5MB max)
                    if (resume.getSize() > 5 * 1024 * 1024) {
                        return ResponseEntity.badRequest().body(
                                ApiResponseDto.error("Resume file size must be less than 5MB")
                        );
                    }

                    // Create uploads directory if it doesn't exist
                    Path uploadsDir = Paths.get("uploads/resumes");
                    Files.createDirectories(uploadsDir);

                    // Generate unique filename
                    String originalFilename = resume.getOriginalFilename();
                    String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                    String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

                    // Save file
                    Path filePath = uploadsDir.resolve(uniqueFilename);
                    Files.copy(resume.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                    resumePath = filePath.toString();

                    log.info("Resume saved to: {}", resumePath);

                } catch (IOException e) {
                    log.error("Error saving resume file: {}", e.getMessage(), e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                            ApiResponseDto.error("Failed to save resume file")
                    );
                }
            }

            // Create registration DTO
            StudentRegistrationDto registrationDto = StudentRegistrationDto.builder()
                    .email(email.trim().toLowerCase())
                    .fullName(fullName.trim())
                    .password(password)
                    .mobileNumber(mobileNumber)
                    .collegeName(collegeName.trim())
                    .yearOfGraduation(yearOfGraduation)
                    .resumePath(resumePath)
                    .build();

            log.info("Created registration DTO: {}", registrationDto);

            // Complete registration via service
            ApiResponseDto<String> response = studentService.completeRegistration(registrationDto);

            log.info("Registration service result: success={}, message={}",
                    response.isSuccess(), response.getMessage());

            if (response.isSuccess()) {
                log.info("Registration completed successfully for: {}", email);
                return ResponseEntity.ok(response);
            } else {
                log.warn("Registration failed for: {} - {}", email, response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Error in complete registration endpoint: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDto.error("Registration failed: " + e.getMessage())
            );
        }
    }

    /**
     * Get all students - Only HR and ADMIN can access
     */
    @GetMapping
    public ResponseEntity<List<Student>> getAllStudents() {
        try {
            log.info("=== Get All Students Endpoint Hit ===");
            log.info("Current user: {}", authService.getCurrentUsername());
            log.info("Current user role: {}", authService.getCurrentUserRole());

            // Check authentication
            if (!authService.isAuthenticated()) {
                log.warn("Unauthenticated access attempt to get all students");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check authorization
            if (!authService.canManageStudents()) {
                log.warn("Unauthorized access attempt to get all students by: {}",
                        authService.getCurrentUsername());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            List<Student> students = studentService.getAllStudents();
            log.info("Retrieved {} students successfully", students.size());
            return ResponseEntity.ok(students);

        } catch (Exception e) {
            log.error("Error fetching students", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get students by role - Only HR and ADMIN can access
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<List<Student>> getStudentsByRole(@PathVariable String role) {
        try {
            log.info("=== Get Students By Role Endpoint Hit: {} ===", role);

            if (!authService.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            if (!authService.canManageStudents()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            try {
                Student.StudentRole studentRole = Student.StudentRole.valueOf(role.toUpperCase());
                List<Student> students = studentService.getStudentsByRole(studentRole);
                log.info("Retrieved {} students with role: {}", students.size(), role);
                return ResponseEntity.ok(students);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid student role requested: {}", role);
                return ResponseEntity.badRequest().build();
            }

        } catch (Exception e) {
            log.error("Error fetching students by role: {}", role, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get incomplete registrations - Only HR and ADMIN can access
     */
    @GetMapping("/incomplete")
    public ResponseEntity<List<Student>> getIncompleteRegistrations() {
        try {
            log.info("=== Get Incomplete Registrations Endpoint Hit ===");

            if (!authService.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            if (!authService.canManageStudents()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            List<Student> students = studentService.getIncompleteRegistrations();
            log.info("Retrieved {} incomplete registrations", students.size());
            return ResponseEntity.ok(students);

        } catch (Exception e) {
            log.error("Error fetching incomplete registrations", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get student statistics - Only HR and ADMIN can access
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Long>> getStudentStatistics() {
        try {
            log.info("=== Get Student Statistics Endpoint Hit ===");
            log.info("Current user: {}", authService.getCurrentUsername());
            log.info("Current user role: {}", authService.getCurrentUserRole());

            if (!authService.isAuthenticated()) {
                log.warn("Unauthenticated access to statistics");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            if (!authService.canManageStudents()) {
                log.warn("Unauthorized access to statistics by: {}", authService.getCurrentUsername());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Map<String, Long> stats = studentService.getStudentStatistics();
            log.info("Retrieved statistics successfully: {}", stats);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error fetching student statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Helper method to determine redirect path based on role
     */
    private String getRedirectPath(String role) {
        return switch (role) {
            case "ZSGS" -> "/student/profile";
            case "PMIS" -> "/interview-management";
            default -> "/dashboard";
        };
    }
}