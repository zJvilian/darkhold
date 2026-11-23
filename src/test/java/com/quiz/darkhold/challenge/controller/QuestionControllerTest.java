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
}
