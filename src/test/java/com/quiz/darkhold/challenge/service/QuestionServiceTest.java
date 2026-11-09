package com.quiz.darkhold.challenge.service;

import com.quiz.darkhold.challenge.dto.QuestionRequest;
import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.challenge.repository.QuestionSetRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionService edit permissions")
class QuestionServiceTest {

    private static final long CALLER_ID = 1L;
    private static final long OWNER_ID = 2L;
    private static final long QUESTION_ID = 10L;

    @Mock
    private QuestionSetRepository questionSetRepository;
    @Mock
    private ChallengeRepository challengeRepository;
    @Mock
    private ImageProcessingService imageProcessingService;

    private QuestionService questionService;
    private QuestionSet question;

    @BeforeEach
    void setUp() {
        questionService = new QuestionService(questionSetRepository, challengeRepository, imageProcessingService);
        Challenge challenge = new Challenge();
        challenge.setId(5L);
        challenge.setChallengeOwner(OWNER_ID);
        question = new QuestionSet();
        question.setId(QUESTION_ID);
        question.setChallenge(challenge);
        when(questionSetRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(final String roleName) {
        User user = new User();
        user.setId(CALLER_ID);
        if (roleName != null) {
            user.addRole(new Role(1L, roleName));
        }
        DarkholdUserDetails details = new DarkholdUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private QuestionRequest request() {
        QuestionRequest request = new QuestionRequest();
        request.setQuestion("Q?");
        return request;
    }

    @Test
    @DisplayName("ADMIN can update another user's question")
    void adminCanUpdate() {
        signIn("ADMIN");
        when(questionSetRepository.save(any(QuestionSet.class))).thenAnswer(inv -> inv.getArgument(0));
        assertNotNull(questionService.updateQuestion(QUESTION_ID, request()));
    }

    @Test
    @DisplayName("ADMIN can delete another user's question")
    void adminCanDelete() {
        signIn("ADMIN");
        assertTrue(questionService.deleteQuestion(QUESTION_ID));
        verify(questionSetRepository).delete(question);
    }

    @Test
    @DisplayName("Plain user cannot update or delete another user's question")
    void plainUserCannotEdit() {
        signIn("USER");
        assertNull(questionService.updateQuestion(QUESTION_ID, request()));
        assertFalse(questionService.deleteQuestion(QUESTION_ID));
        verify(questionSetRepository, never()).save(any());
        verify(questionSetRepository, never()).delete(any());
    }
}
