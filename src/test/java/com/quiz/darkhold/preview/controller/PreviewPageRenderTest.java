package com.quiz.darkhold.preview.controller;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.preview.model.PreviewInfo;
import com.quiz.darkhold.preview.service.PreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayDeque;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PreviewPageRenderTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PreviewService previewService;

    @Test
    void previewRendersEveryQuestionTypeEvenWithoutCorrectOptions() throws Exception {
        when(previewService.fetchQuestions("1")).thenReturn(mixedQuiz());

        mvc.perform(post("/preconfigure").param("challenges", "1").with(user("host")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paris")))
                .andExpect(content().string(containsString("Last question")))
                .andExpect(content().string(containsString("</html>")));
    }

    private PreviewInfo mixedQuiz() {
        var typeAnswer = question(QuestionType.TYPE_ANSWER, "Capital of France?");
        typeAnswer.setAnswer1("Paris");
        var multipleChoice = question(QuestionType.MULTIPLE_CHOICE, "Last question");
        multipleChoice.setAnswer1("one");
        multipleChoice.setCorrectOptions("A");
        var info = new PreviewInfo();
        info.setChallengeId("1");
        info.setChallengeName("Mixed");
        info.setQuestionSets(new ArrayDeque<>(java.util.List.of(typeAnswer, multipleChoice)));
        return info;
    }

    private QuestionSet question(final QuestionType type, final String text) {
        var question = new QuestionSet();
        question.setQuestionType(type);
        question.setQuestion(text);
        return question;
    }
}
