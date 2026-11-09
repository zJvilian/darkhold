package com.quiz.darkhold.user.service;

import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAdminPasswordInitializerTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private User admin(final String password) {
        User admin = new User();
        admin.setPassword(encoder.encode(password));
        when(repository.findByEmail("admin@admin.com")).thenReturn(admin);
        return admin;
    }

    @Test
    void replacesDefaultPasswordWithConfiguredOne() {
        User admin = admin("admin");
        new DefaultAdminPasswordInitializer(repository, encoder, "s3cret-pass").run(null);
        assertTrue(encoder.matches("s3cret-pass", admin.getPassword()));
        assertFalse(encoder.matches("admin", admin.getPassword()));
        verify(repository).save(admin);
    }

    @Test
    void generatesPasswordWhenNoneConfigured() {
        User admin = admin("admin");
        new DefaultAdminPasswordInitializer(repository, encoder, "").run(null);
        assertFalse(encoder.matches("admin", admin.getPassword()));
        verify(repository).save(admin);
    }

    @Test
    void leavesChangedPasswordAlone() {
        admin("already-changed");
        new DefaultAdminPasswordInitializer(repository, encoder, "s3cret-pass").run(null);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /** Spring Session JDBC stores the security context with Java serialization. */
    @Test
    void userDetailsAreSerializable() throws IOException {
        User user = new User();
        user.addRole(new Role(1L, "ADMIN"));
        try (var out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(new DarkholdUserDetails(user));
        }
    }
}
