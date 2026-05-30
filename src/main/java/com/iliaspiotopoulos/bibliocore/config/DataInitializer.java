package com.iliaspiotopoulos.bibliocore.config;

import com.iliaspiotopoulos.bibliocore.model.entity.User;
import com.iliaspiotopoulos.bibliocore.model.enums.Role;
import com.iliaspiotopoulos.bibliocore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds initial data for development and testing purposes.
 * Only active in local-h2 and docker-pg profiles.
 *
 * Default admin credentials:
 *   Email: admin@bibliocore.com
 *   Password: Admin123!
 */
@Component
@Profile({"local-h2", "docker-pg"})
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_ADMIN_EMAIL = "admin@bibliocore.com";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdminUser();
    }

    private void seedAdminUser() {
        if (userRepository.existsByEmail(DEFAULT_ADMIN_EMAIL)) {
            log.info("Admin user already exists, skipping seed");
            return;
        }

        User admin = User.builder()
                .email(DEFAULT_ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                .role(Role.ROLE_ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Created default admin user: {}", DEFAULT_ADMIN_EMAIL);
    }
}