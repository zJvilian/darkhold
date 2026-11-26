package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "darkhold.registration.enabled=false")
@AutoConfigureMockMvc
@Transactional
class RegistrationDisabledIntegrationTest {
    private static final String EMAIL = "registration-disabled@example.invalid";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void registrationPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/registration"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void directPostIsForbiddenAndCreatesNoAccount() throws Exception {
        mockMvc.perform(post("/registration").with(csrf())
                        .param("firstName", "Registration").param("lastName", "Disabled")
                        .param("email", EMAIL).param("password", "Registration-test-123!"))
                .andExpect(status().isForbidden());
        assertNull(userService.findByUsername(EMAIL));
    }

    @Test
    void loginPageHidesSignupLink() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/registration"))));
    }
}
