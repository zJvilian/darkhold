package com.quiz.darkhold.challenge.controller;

import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Create quiz → draft in the editor → finish → playable.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:draft-flow-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DraftQuizFlowIntegrationTest {
    private static final String OWNER = "admin@admin.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ChallengeRepository challenges;
    @Autowired
    private UserRepository users;
    @Autowired
    private ObjectMapper mapper;

    private RequestPostProcessor owner() {
        return user(new DarkholdUserDetails(users.findByEmail(OWNER)));
    }

    private long createEmptyQuiz() throws Exception {
        String body = mvc.perform(post("/save_challenge").with(owner()).with(csrf())
                        .param("title", "Draft " + UUID.randomUUID()).param("description", ""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("challengeId").asLong();
    }

    private void addQuestion(final long challengeId) throws Exception {
        mvc.perform(post("/api/question").with(owner()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":" + challengeId + ",\"question\":\"2+2?\","
                                + "\"questionType\":\"MULTIPLE_CHOICE\",\"answer1\":\"4\",\"answer2\":\"5\","
                                + "\"correctOptions\":\"A\",\"points\":1000}"))
                .andExpect(status().isOk());
    }

    @Test
    void newQuizIsADraftInTheEditorAndNeedsAQuestionToFinish() throws Exception {
        long id = createEmptyQuiz();
        assertTrue(challenges.findById(id).orElseThrow().isDraft());
        mvc.perform(get("/edit_challenge/" + id).with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"draftBannerTitle\"")))
                .andExpect(content().string(containsString("class=\"create-steps\"")));
        mvc.perform(post("/challenge/" + id + "/finish").with(owner()).with(csrf()))
                .andExpect(status().isBadRequest());
        assertTrue(challenges.findById(id).orElseThrow().isDraft());
    }

    @Test
    void draftCannotBePlayed() throws Exception {
        long id = createEmptyQuiz();
        addQuestion(id);
        mvc.perform(get("/preconfigure").param("challenges", String.valueOf(id)).with(owner()))
                .andExpect(redirectedUrl("/edit_challenge/" + id));
        mvc.perform(post("/live/host").with(owner()).with(csrf()).param("challenge_id", String.valueOf(id)))
                .andExpect(redirectedUrl("/edit_challenge/" + id))
                .andExpect(flash().attributeExists("liveError"));
        mvc.perform(post("/start_practice").with(owner()).with(csrf()).param("challenge_id", String.valueOf(id)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void finishingMakesTheQuizPlayable() throws Exception {
        long id = createEmptyQuiz();
        addQuestion(id);
        mvc.perform(post("/challenge/" + id + "/finish").with(owner()).with(csrf()))
                .andExpect(status().isOk());
        assertFalse(challenges.findById(id).orElseThrow().isDraft());
        mvc.perform(get("/preconfigure").param("challenges", String.valueOf(id)).with(owner()))
                .andExpect(status().isOk())
                .andExpect(view().name("challenge/preview"));
        mvc.perform(get("/edit_challenge/" + id).with(owner()))
                .andExpect(content().string(not(containsString("id=\"draftBannerTitle\""))));
    }

    @Test
    void quizListMarksDrafts() throws Exception {
        long id = createEmptyQuiz();
        mvc.perform(get("/viewChallenge").with(owner()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-draft=\"true\"")))
                .andExpect(content().string(containsString("class=\"draft-badge\"")));
        assertTrue(challenges.findById(id).orElseThrow().isDraft());
    }

    @Test
    void jsonImportBecomesADraftAndUsesTheTypedTitle() throws Exception {
        String title = "Typed " + UUID.randomUUID();
        var file = new MockMultipartFile("file", "quiz.json", "application/json",
                ("{\"title\":\"From file\",\"description\":\"d\",\"questions\":[{\"question\":\"Q?\","
                        + "\"answer1\":\"A\",\"answer2\":\"B\",\"correctOptions\":\"A\"}]}").getBytes());
        String body = mvc.perform(multipart("/import_challenge/json").file(file).param("title", title)
                        .with(owner()).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var imported = challenges.findById(mapper.readTree(body).path("challengeId").asLong()).orElseThrow();
        assertEquals(title, imported.getTitle());
        assertTrue(imported.isDraft());
    }

    @Test
    void otherUsersCannotFinishSomeoneElsesDraft() throws Exception {
        long id = createEmptyQuiz();
        addQuestion(id);
        var participant = user(new DarkholdUserDetails(users.findByEmail("participant@guest.com")));
        mvc.perform(post("/challenge/" + id + "/finish").with(participant).with(csrf()))
                .andExpect(status().is4xxClientError());
        assertTrue(challenges.findById(id).orElseThrow().isDraft());
    }
}
