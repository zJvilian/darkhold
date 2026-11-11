package com.quiz.darkhold.init;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig Tests")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    @DisplayName("Should allow access to home page")
    void shouldAllowAccessToHomePage() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should allow access to registration page")
    void shouldAllowAccessToRegistration() throws Exception {
        mockMvc.perform(get("/registration"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should allow anonymous access to legal pages")
    void shouldAllowAccessToLegalPages() throws Exception {
        mockMvc.perform(get("/impressum")).andExpect(status().isOk());
        mockMvc.perform(get("/datenschutz")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should allow access to login page")
    void shouldAllowAccessToLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should redirect protected path to login")
    void shouldRedirectProtectedPathToLogin() throws Exception {
        mockMvc.perform(get("/viewchallenges"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("PIN guests (signed in, no role) may play but cannot open account pages")
    void shouldKeepGuestsOutOfAccountPages() throws Exception {
        var guest = user("Alice").authorities(java.util.List.of());
        mockMvc.perform(get("/viewchallenges").with(guest)).andExpect(status().isForbidden());
        mockMvc.perform(get("/account").with(guest)).andExpect(status().isForbidden());
        mockMvc.perform(post("/interstitial").with(guest).with(csrf()).param("quiz_pin", "12345"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should redirect active-games to login")
    void shouldRedirectActiveGamesToLogin() throws Exception {
        mockMvc.perform(get("/active-games"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Should redirect edit challenge to login")
    void shouldRedirectEditChallengeToLogin() throws Exception {
        mockMvc.perform(get("/edit_challenge/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Should have BCryptPasswordEncoder bean")
    void shouldHaveBCryptPasswordEncoderBean() {
        assertNotNull(passwordEncoder);
        assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder);
    }

    @Test
    @DisplayName("Should have AuthenticationManager bean")
    void shouldHaveAuthenticationManagerBean() {
        assertNotNull(authenticationManager);
    }

    @Test
    @DisplayName("Should encode passwords with BCrypt")
    void shouldEncodePasswordsWithBCrypt() {
        var encoded = passwordEncoder.encode("testPassword");
        assertTrue(passwordEncoder.matches("testPassword", encoded));
    }

    @Test
    @DisplayName("Repeated bad logins from one IP block even the correct password")
    void loginIsThrottledPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("admin@admin.com", "wrong", "10.1.1.1"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mockMvc.perform(login("admin@admin.com", "admin", "10.1.1.1")).andExpect(redirectedUrl("/login?error"));
        // another client is unaffected
        mockMvc.perform(login("admin@admin.com", "admin", "10.1.1.2")).andExpect(redirectedUrl("/"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            final String username, final String password, final String ip) {
        return post("/login").with(csrf()).param("username", username).param("password", password)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    @Test
    @DisplayName("Game management is open to ADMIN and GAME_MANAGER only")
    void gameManagementIsRoleRestricted() throws Exception {
        mockMvc.perform(get("/gameManagement").with(user("gm").authorities(() -> "GAME_MANAGER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/gameManagement").with(user("p").authorities(() -> "PARTICIPANT")))
                .andExpect(status().isForbidden());
    }
}
