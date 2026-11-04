package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.UserRepository;
import com.quiz.darkhold.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Not transactional on purpose: the foreign-key violation only appears when the delete really commits. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user-deletion;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class UserDeletionIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService service;
    @Autowired
    private UserRepository users;
    @Autowired
    private ChallengeRepository challenges;

    private User createOwner() {
        User owner = new User();
        owner.setFirstName("Quiz");
        owner.setLastName("Owner");
        owner.setEmail("owner@example.invalid");
        owner.setPassword("Test-password-123!");
        owner.setEnabled(true);
        owner.addRole(service.listRoles().stream()
                .filter(role -> role.getName().equals("GUEST")).findFirst().orElseThrow());
        return service.save(owner);
    }

    @Test
    void deletingAnAccountThatOwnsAQuizShowsAMessageInsteadOfAnError() throws Exception {
        User owner = createOwner();
        Challenge quiz = new Challenge();
        quiz.setTitle("Test1");
        quiz.setChallengeOwner(owner.getId());
        quiz.setCreatedOn(LocalDateTime.now());
        challenges.save(quiz);

        mvc.perform(post("/user/delete/" + owner.getId()).param("lang", "en")
                        .with(user(new DarkholdUserDetails(users.findByEmail("admin@admin.com")))).with(csrf()))
                .andExpect(redirectedUrl("/userManagement"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertTrue(users.existsById(owner.getId()));
    }
}
