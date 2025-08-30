package com.kalvitrack.kalvitrack_backend.config;

import com.kalvitrack.kalvitrack_backend.entity.User;
import com.kalvitrack.kalvitrack_backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setEmail("testuser13@gmail.com");
            admin.setHashedPassword(passwordEncoder.encode("Testuser@123")); // ✅ fixed
            admin.setRole(User.Role.ADMIN); // ✅ enum instead of string
            userRepository.save(admin);

            System.out.println("✅ Default admin user inserted into external DB");
        } else {
            System.out.println("ℹ️ Users already exist, skipping admin creation.");
        }
    }
}
