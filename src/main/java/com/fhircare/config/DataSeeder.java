package com.fhircare.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedAdmin(JdbcTemplate jdbc, PasswordEncoder encoder) {
        return args -> {
            try {
                Integer count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, "admin");
                if (count == null || count == 0) {
                    jdbc.update(
                        "INSERT INTO users (username, email, password_hash, full_name, role, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NOW())",
                        "admin", "admin@fhircare.local", encoder.encode("admin123"),
                        "System Administrator", "ADMIN", "ACTIVE");
                    System.out.println("Admin user created: admin / admin123");
                }
            } catch (Exception e) {
                System.out.println("Admin seed skipped: " + e.getMessage());
            }
        };
    }
}