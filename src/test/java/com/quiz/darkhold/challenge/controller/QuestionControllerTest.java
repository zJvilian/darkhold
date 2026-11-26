package com.quiz.darkhold.challenge.controller;

import com.quiz.darkhold.challenge.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionControllerTest {

    private final QuestionService questionService = mock(QuestionService.class);
    private final QuestionController controller = new QuestionController(questionService);

    @Test
    void bulkDeleteRejectsAnEmptyOrMissingList() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.bulkDeleteQuestions(List.of()).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.bulkDeleteQuestions(null).getStatusCode());
        verify(questionService, never()).bulkDeleteQuestions(anyList());
    }

    @Test
    void bulkDeleteReportsTheDeletedCount() {
        when(questionService.bulkDeleteQuestions(List.of(1L, 2L))).thenReturn(2);
        var response = controller.bulkDeleteQuestions(List.of(1L, 2L));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().get("deleted"));
    }

    @Test
    void uploadAndDeleteAnswerImageDelegateToService() {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "image", "reveal.png", "image/png", new byte[]{1});
        when(questionService.uploadAnswerImage(7L, file)).thenReturn("/question-images/1/7/answer_1.png");
        var uploaded = controller.uploadAnswerImage(7L, file);
        assertEquals(HttpStatus.OK, uploaded.getStatusCode());
        assertEquals("/question-images/1/7/answer_1.png", uploaded.getBody().get("answerImageUrl"));

        when(questionService.deleteAnswerImage(7L)).thenReturn(true, false);
        assertEquals(HttpStatus.OK, controller.deleteAnswerImage(7L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteAnswerImage(7L).getStatusCode());
    }
}
