package com.quiz.darkhold.user.service;

import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * The Flyway seed (V2) creates admin@admin.com with the well-known password "admin". Outside H2 development
 * that must not survive the first start, so replace it with the configured or a generated password.
 */
@Component
@Profile("postgres")
public class DefaultAdminPasswordInitializer implements ApplicationRunner {
    static final String ADMIN_EMAIL = "admin@admin.com";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int GENERATED_LENGTH = 24;
    private static final Logger LOGGER = LogManager.getLogger(DefaultAdminPasswordInitializer.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;
    private final String configuredPassword;

    public DefaultAdminPasswordInitializer(final UserRepository userRepository, final BCryptPasswordEncoder encoder,
                                           @Value("${darkhold.admin.password:}") final String configuredPassword) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(final ApplicationArguments args) {
        User admin = userRepository.findByEmail(ADMIN_EMAIL);
        if (admin == null || !encoder.matches(DEFAULT_PASSWORD, admin.getPassword())) {
            return;
        }
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String password = generated ? generatePassword() : configuredPassword;
        admin.setPassword(encoder.encode(password));
        userRepository.save(admin);
        LOGGER.warn("Replaced the default password of {} with {}", ADMIN_EMAIL,
                generated ? "a generated one: " + password + " (set DARKHOLD_ADMIN_PASSWORD to choose your own)"
                        : "DARKHOLD_ADMIN_PASSWORD");
    }

    private static String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
