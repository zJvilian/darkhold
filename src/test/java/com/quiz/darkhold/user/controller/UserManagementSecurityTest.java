package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.UserRepository;
import com.quiz.darkhold.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserManagementSecurityTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService service;
    @Autowired
    private UserRepository users;
    private User guest;

    @BeforeEach
    void createGuest() {
        guest = new User();
        guest.setFirstName("Permission");
        guest.setLastName("Test");
        guest.setEmail("permissions-" + UUID.randomUUID() + "@example.invalid");
        guest.setPassword("Test-password-123!");
        guest.setEnabled(true);
        guest.addRole(service.listRoles().stream()
                .filter(role -> role.getName().equals("GUEST")).findFirst().orElseThrow());
        guest = service.save(guest);
    }

    private RequestPostProcessor admin() {
        return user(new DarkholdUserDetails(users.findByEmail("admin@admin.com")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST", "GAME_MANAGER", "PARTICIPANT", "ROLE_MODERATOR"})
    void nonAdministratorsCannotReadOrChangeAccounts(final String authority) throws Exception {
        var actor = user("test").authorities(new SimpleGrantedAuthority(authority));
        for (String path : new String[]{"/userManagement", "/user/page/1", "/user/create",
                "/user/edit/" + guest.getId(), "/admin/users/" + guest.getId() + "/quizzes",
                "/admin", "/user/delete/" + guest.getId()}) {
            mvc.perform(get(path).with(actor)).andExpect(status().isForbidden());
        }
        for (String path : new String[]{"/users/save", "/users/check_email",
                "/user/delete/" + guest.getId(), "/users/" + guest.getId() + "/enabled/false"}) {
            mvc.perform(post(path).with(actor).with(csrf()).param("id", guest.getId().toString())
                    .param("roles", "1")).andExpect(status().isForbidden());
        }
        assertTrue(users.findById(guest.getId()).orElseThrow().getEnabled());
        assertEquals("GUEST", users.findById(guest.getId()).orElseThrow().getRoles().iterator().next().getName());
    }

    @Autowired
    private com.quiz.darkhold.challenge.repository.ChallengeRepository challenges;

    @Test
    void administratorCanInspectOnlyTheSelectedUsersQuizzes() throws Exception {
        String path = "/admin/users/" + guest.getId() + "/quizzes";
        mvc.perform(get(path).with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("This user owns no quizzes.")));
        var quiz = new com.quiz.darkhold.challenge.entity.Challenge();
        quiz.setTitle("Owned quiz " + UUID.randomUUID());
        quiz.setDescription("Inspection description");
        quiz.setChallengeOwner(guest.getId());
        challenges.saveAndFlush(quiz);
        mvc.perform(get(path).with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString(quiz.getTitle())))
                .andExpect(content().string(containsString("This quiz has no questions.")));
        var question = new com.quiz.darkhold.challenge.entity.QuestionSet();
        question.setQuestion("A question owned by another user?");
        question.setChallenge(quiz);
        quiz.setQuestionSets(new java.util.ArrayList<>(java.util.List.of(question)));
        challenges.saveAndFlush(quiz);
        mvc.perform(get(path).with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString(question.getQuestion())));
        Long adminId = users.findByEmail("admin@admin.com").getId();
        mvc.perform(get("/admin/users/" + adminId + "/quizzes").with(admin()))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(quiz.getTitle()))));
        mvc.perform(get("/userManagement").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/users/" + adminId + "/quizzes")))
                .andExpect(content().string(containsString(">View quizzes</span>")));
        mvc.perform(get("/admin/users/9223372036854775807/quizzes").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Autowired
    private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder;

    @Test
    void passwordResetRedirectsWithFeedbackAndBlankPasswordPreservesIt() throws Exception {
        String path = "/user/edit/" + guest.getId();
        mvc.perform(get(path).with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("/scripts/csrf-manager.js")))
                .andExpect(content().string(not(containsString(guest.getPassword()))));
        mvc.perform(post("/users/check_email").with(admin()).with(csrf())
                .param("id", guest.getId().toString()).param("email", guest.getEmail()))
                .andExpect(status().isOk()).andExpect(content().string("OK"));
        String replacement = "Reset-password-123!";
        for (String password : new String[]{replacement, ""}) {
            mvc.perform(multipart("/users/save").file(new MockMultipartFile("image", new byte[0]))
                    .with(admin()).with(csrf()).param("id", guest.getId().toString())
                    .param("firstName", guest.getFirstName()).param("lastName", guest.getLastName())
                    .param("email", guest.getEmail()).param("password", password).param("enabled", "true")
                    .param("roles", guest.getRoles().iterator().next().getId().toString()))
                    .andExpect(redirectedUrl("/userManagement"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                            .attribute("message", "The user has been saved successfully"));
            String stored = users.findById(guest.getId()).orElseThrow().getPassword();
            assertTrue(encoder.matches(replacement, stored));
            assertFalse(encoder.matches("Test-password-123!", stored));
        }
    }

    @Test
    void navigationMatchesActualDatabaseAuthorities() throws Exception {
        mvc.perform(get("/options").with(user(new DarkholdUserDetails(guest))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("onclick=\"manageUsers();\""))))
                .andExpect(content().string(not(containsString("href=\"/admin\""))));
        mvc.perform(get("/options").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("onclick=\"manageUsers();\"")));
        mvc.perform(get("/admin").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/userManagement").with(admin()).param("lang", "de")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Möchtest du das Benutzerkonto")));
        mvc.perform(get("/user/edit/" + guest.getId()).with(admin())).andExpect(status().isOk());
    }

    @Test
    void administratorCanChangeRolesWithCsrf() throws Exception {
        var adminRole = service.listRoles().stream().filter(role -> role.getName().equals("ADMIN"))
                .findFirst().orElseThrow();
        mvc.perform(multipart("/users/save").file(new MockMultipartFile("image", new byte[0]))
                        .with(admin()).with(csrf()).param("id", guest.getId().toString())
                        .param("firstName", guest.getFirstName()).param("lastName", guest.getLastName())
                        .param("email", guest.getEmail()).param("password", "").param("enabled", "true")
                        .param("roles", adminRole.getId().toString()))
                .andExpect(redirectedUrl("/userManagement"));
        assertEquals("ADMIN", users.findById(guest.getId()).orElseThrow().getRoles().iterator().next().getName());
    }

    @Test
    void deletionAndStatusChangesRequirePostAndCsrf() throws Exception {
        for (String path : new String[]{"/user/delete/" + guest.getId(),
                "/users/" + guest.getId() + "/enabled/false"}) {
            mvc.perform(get(path).with(admin())).andExpect(status().isMethodNotAllowed());
            mvc.perform(post(path).with(admin())).andExpect(status().isForbidden());
        }
        assertTrue(users.findById(guest.getId()).orElseThrow().getEnabled());
        mvc.perform(post("/users/" + guest.getId() + "/enabled/false").with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/userManagement"));
        mvc.perform(post("/user/delete/" + guest.getId()).with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/userManagement"));
        assertFalse(users.existsById(guest.getId()));
    }

    @Test
    void anonymousRequestsCannotManageAccounts() throws Exception {
        mvc.perform(get("/userManagement")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/user/delete/" + guest.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(users.existsById(guest.getId()));
    }
}
