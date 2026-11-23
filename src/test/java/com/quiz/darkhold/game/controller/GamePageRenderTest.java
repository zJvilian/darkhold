package com.quiz.darkhold.game.controller;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.game.model.GameMode;
import com.quiz.darkhold.game.model.QuestionPointer;
import com.quiz.darkhold.game.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GamePageRenderTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GameService gameService;

    @Test
    void multiplayerPageHidesTheAnswerAndLetsGuestsPickSeveralOptions() throws Exception {
        when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(primesQuestion());
        when(gameService.getGameMode(anyString())).thenReturn(GameMode.MULTIPLAYER);
        mvc.perform(post("/game").param("quizPin", "12345").with(user("Guest").authorities(List.of())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"submitChoices\""),
                        containsString("data-src=\"/question-images/1/1/clip.mp3\""),
                        containsString("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"),
                        containsString("name=\"_csrf\""),
                        not(containsString("value=\"A,C\"")),
                        not(containsString("id=\"questionImage\"")))));
    }

    private QuestionPointer primesQuestion() {
        QuestionSet question = new QuestionSet();
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setQuestion("Pick the primes");
        question.setAnswer1("Two");
        question.setAnswer2("Four");
        question.setAnswer3("Five");
        question.setCorrectOptions("A,C");
        question.setImageUrl("/question-images/1/1/clip.mp3");
        question.setVideoUrl("https://youtu.be/dQw4w9WgXcQ");
        QuestionPointer pointer = new QuestionPointer();
        pointer.setCurrentQuestion(question);
        pointer.setTotalQuestionCount(1);
        return pointer;
    }
}
