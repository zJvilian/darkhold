package com.quiz.darkhold.account.controller;

import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.UserRepository;
import com.quiz.darkhold.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {
    private static final String OLD = "Old-password-123!";
    private static final String NEW = "New-password-456!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService service;
    @Autowired
    private UserRepository users;
    @Autowired
    private BCryptPasswordEncoder encoder;
    private User me;

    @BeforeEach
    void createUser() {
        me = new User();
        me.setFirstName("Self");
        me.setLastName("Edit");
        me.setEmail("self-" + UUID.randomUUID() + "@example.invalid");
        me.setPassword(OLD);
        me.setEnabled(true);
        me.addRole(service.listRoles().stream()
                .filter(role -> role.getName().equals("GUEST")).findFirst().orElseThrow());
        me = service.save(me);
    }

    private RequestPostProcessor asMe() {
        return user(new DarkholdUserDetails(me));
    }

    private void changePassword(final String current, final String next, final String confirm) throws Exception {
        mvc.perform(post("/account/password").with(asMe()).with(csrf()).param("currentPassword", current)
                .param("newPassword", next).param("confirmPassword", confirm)).andExpect(redirectedUrl("/account"));
    }

    private boolean passwordIs(final String raw) {
        return encoder.matches(raw, users.findById(me.getId()).orElseThrow().getPassword());
    }

    @Test
    void passwordChangeNeedsCorrectCurrentPasswordAndStrongMatchingNewOne() throws Exception {
        changePassword("wrong", NEW, NEW);
        changePassword(OLD, NEW, "different");
        changePassword(OLD, "weak", "weak");
        assertTrue(passwordIs(OLD));
        changePassword(OLD, NEW, NEW);
        assertTrue(passwordIs(NEW));
        assertFalse(passwordIs(OLD));
    }

    private Cookie login() throws Exception {
        var result = mvc.perform(formLogin().user(me.getEmail()).password(OLD)).andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertEquals(10 * 24 * 3600, session.getMaxAge(), "login must survive browser restarts for 10 days");
        return session;
    }

    @Test
    void passwordChangeSignsOutOtherStoredSessionsButNotTheCurrentOne() throws Exception {
        Cookie other = login();
        Cookie mine = login();
        mvc.perform(get("/account").cookie(other)).andExpect(status().isOk());
        mvc.perform(post("/account/password").cookie(mine).with(csrf()).param("currentPassword", OLD)
                .param("newPassword", NEW).param("confirmPassword", NEW)).andExpect(redirectedUrl("/account"));
        mvc.perform(get("/account").cookie(mine)).andExpect(status().isOk());
        mvc.perform(get("/account").cookie(other)).andExpect(status().is3xxRedirection());
    }

    @Test
    void navbarShowsTheNewNameAfterAProfileUpdateOnTheStoredSession() throws Exception {
        Cookie session = login();
        var none = new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);
        mvc.perform(multipart("/account/profile").file(none).cookie(session).with(csrf()).param("firstName", "Renamed")
                .param("lastName", "Edit").param("email", me.getEmail())).andExpect(flash().attributeExists("message"));
        mvc.perform(get("/account").cookie(session)).andExpect(content().string(containsString("Renamed")));
    }

    private void saveProfile(final String first, final String email, final String attribute) throws Exception {
        var none = new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);
        mvc.perform(multipart("/account/profile").file(none).with(asMe()).with(csrf())
                .param("firstName", first).param("lastName", "Name").param("email", email)
                .param("currentPassword", OLD)).andExpect(flash().attributeExists(attribute));
    }

    @Test
    void changingTheEmailNeedsTheCurrentPassword() throws Exception {
        var none = new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);
        mvc.perform(multipart("/account/profile").file(none).with(asMe()).with(csrf()).param("firstName", "New")
                .param("lastName", "Name").param("email", "nopass@example.invalid").param("currentPassword", "wrong"))
                .andExpect(flash().attribute("errorMessage", "account.error.current"));
        assertEquals(me.getEmail(), users.findById(me.getId()).orElseThrow().getEmail());
    }

    @Test
    void profileUpdateChangesNamesAndEmailAndRejectsBadInput() throws Exception {
        var text = new MockMultipartFile("image", "x.txt", "text/plain", "hi".getBytes());
        mvc.perform(multipart("/account/profile").file(text).with(asMe()).with(csrf())
                .param("firstName", "New").param("lastName", "Name").param("email", me.getEmail()))
                .andExpect(flash().attribute("errorMessage", "account.error.photo"));
        saveProfile("New", "not-an-email", "errorMessage");
        saveProfile("New", "admin@admin.com", "errorMessage");
        assertEquals("Self", users.findById(me.getId()).orElseThrow().getFirstName());
        String email = "changed-" + UUID.randomUUID() + "@example.invalid";
        saveProfile(" New ", email, "message");
        User saved = users.findById(me.getId()).orElseThrow();
        assertEquals("New", saved.getFirstName());
        assertEquals(email, saved.getEmail());
    }
}
