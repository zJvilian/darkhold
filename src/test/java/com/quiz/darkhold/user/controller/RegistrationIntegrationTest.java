package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    private MockHttpServletRequestBuilder registration(final String password) {
        return post("/registration").with(csrf())
                .param("firstName", "Registration").param("lastName", "Test")
                .param("email", "registration-test@example.invalid").param("password", password);
    }

    @Test
    void formRendersOneValidCsrfField() throws Exception {
        var response = mockMvc.perform(get("/registration"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("${_csrf."))))
                .andExpect(content().string(matchesPattern("(?s).*name=\"_csrf\" value=\"[^\"]+\".*")))
                .andReturn().getResponse();
        assertEquals(1, response.getContentAsString().split("name=\"_csrf\"", -1).length - 1);
    }

    @Test
    void newAccountCanSignIn() throws Exception {
        mockMvc.perform(registration("Registration-test-123!"))
                .andExpect(status().isOk()).andExpect(view().name("index"));
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "registration-test@example.invalid")
                        .param("password", "Registration-test-123!"))
                .andExpect(status().is3xxRedirection()).andExpect(authenticated());
    }

    @Test
    void registrationCannotOverwriteAnAccountOrChoosePrivilegedRoles() throws Exception {
        var admin = userService.findByUsername("admin@admin.com");
        final String originalHash = admin.getPassword();
        final String originalEmail = admin.getEmail();
        mockMvc.perform(registration("Registration-test-123!").param("id", admin.getId().toString())
                        .param("roles", "1").param("enabled", "false").param("photo", "forged.png"))
                .andExpect(status().isOk());
        var registered = userService.findByUsername("registration-test@example.invalid");
        assertNotEquals(admin.getId(), registered.getId());
        assertEquals("GUEST", registered.getRoles().iterator().next().getName());
        assertEquals(originalHash, userService.findByUsername(originalEmail).getPassword());
    }

    @Test
    void duplicateEmailReturnsFormAndPreservesExistingAccount() throws Exception {
        mockMvc.perform(registration("Original-password-123!"))
                .andExpect(status().isOk());
        String originalHash = userService.findByUsername("registration-test@example.invalid").getPassword();
        mockMvc.perform(registration("Different-password-123!"))
                .andExpect(status().isOk()).andExpect(view().name("registration"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(model().attributeHasFieldErrors("user", "email"))
                .andExpect(content().string(containsString("An account with this email already exists.")))
                .andExpect(content().string(containsString("registration-test@example.invalid")))
                .andExpect(content().string(not(containsString("Different-password-123!"))))
                .andExpect(content().string(not(containsString(originalHash))));
        assertEquals(originalHash, userService.findByUsername("registration-test@example.invalid").getPassword());
    }
}
