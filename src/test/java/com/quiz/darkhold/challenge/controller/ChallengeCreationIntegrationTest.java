package com.quiz.darkhold.challenge.controller;

import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.service.ChallengeService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChallengeCreationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChallengeService challengeService;

    private Cookie renderedToken() throws Exception {
        var page = mockMvc.perform(get("/create_challenge_form"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("${_csrf."))))
                .andExpect(content().string(containsString("/scripts/csrf-manager.js")))
                .andReturn();
        Cookie token = page.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(token);
        return token;
    }

    @Test
    @WithMockUser
    void homePageCreateLinkOpensQuizForm() throws Exception {
        mockMvc.perform(get("/challenge/create"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"fileUploadForm\"")))
                .andExpect(content().string(containsString("/scripts/create-challenge-scripts.js")));
    }

    @Test
    @WithMockUser
    void createsQuizUsingTokenFromRenderedForm() throws Exception {
        Cookie token = renderedToken();
        Challenge challenge = new Challenge();
        challenge.setId(42L);
        when(challengeService.createEmptyChallenge("Quiz", "")).thenReturn(challenge);
        mockMvc.perform(multipart("/save_challenge").param("title", "Quiz").param("description", "")
                        .cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.challengeId").value(42));

    }

    @Test
    @WithMockUser
    void uploadsQuizUsingTokenFromRenderedForm() throws Exception {
        Cookie token = renderedToken();
        when(challengeService.readProcessAndSaveChallenge(any(), eq("Quiz"), eq(""))).thenReturn(43L);
        mockMvc.perform(multipart("/upload_challenge")
                        .file(new MockMultipartFile("upload", "quiz.xlsx", "application/octet-stream", new byte[]{1}))
                        .param("title", "Quiz").param("description", "")
                        .cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.challengeId").value(43));
    }

    @Test
    @WithMockUser
    void stillRejectsCreationWithoutCsrf() throws Exception {
        mockMvc.perform(post("/save_challenge").param("title", "Quiz").param("description", ""))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
