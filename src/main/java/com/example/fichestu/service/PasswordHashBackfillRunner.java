package com.example.fichestu.service;

import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.UserRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PasswordHashBackfillRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public PasswordHashBackfillRunner(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<UserEntity> users = userRepository.findAll();
        for (UserEntity user : users) {
            String passwordHash = user.getPasswordHash();
            if (passwordHash == null || passwordHash.isBlank()) {
                continue;
            }
            if (isBcryptHash(passwordHash)) {
                continue;
            }
            if ("BOT".equalsIgnoreCase(passwordHash)) {
                user.setPasswordHash(null);
                continue;
            }

            user.setPasswordHash(passwordEncoder.encode(passwordHash));
        }
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
