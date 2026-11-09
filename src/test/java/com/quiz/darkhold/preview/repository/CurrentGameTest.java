package com.quiz.darkhold.preview.repository;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.preview.entity.CurrentGameSession;
import com.quiz.darkhold.preview.model.PublishInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentGameTest {
    @Mock
    private CurrentGameSessionRepository repository;

    @InjectMocks
    private CurrentGame currentGame;

    private CurrentGameSession createMockSession() {
        CurrentGameSession session = new CurrentGameSession("1234", "admin");

        List<String> users = new ArrayList<>();
        users.add("admin");
        users.add("tester");
        session.setUsersList(users);

        List<QuestionSet> questions = new ArrayList<>();
        QuestionSet questionSet = new QuestionSet();
        questionSet.setQuestion("Q1");
        questions.add(questionSet);
        session.setQuestionsList(questions);

        session.setCurrentQuestionNo(5);

        var scores = new java.util.HashMap<String, Integer>();
        scores.put("admin", 5);
        session.setScoresMap(scores);

        return session;
    }

    @Test
    void saveCurrentStatus() {
        var publishInfo = new PublishInfo();
        publishInfo.setPin("1234");
        publishInfo.setModerator("admin");
        Assertions.assertAll(() -> currentGame.saveCurrentStatus(publishInfo, new ArrayDeque<>()));
    }

    @Test
    void getActiveUsersInGame() {
        CurrentGameSession session = createMockSession();
        when(repository.findByPin("1234")).thenReturn(Optional.of(session));

        var users = currentGame.getActiveUsersInGame("1234");
        Assertions.assertAll("name",
                () -> Assertions.assertEquals("admin", users.getFirst()),
                () -> Assertions.assertEquals("tester", users.get(1))
        );
    }

    @Test
    void saveUserToActiveGame() {
        CurrentGameSession session = createMockSession();
        when(repository.lockByPin("1234")).thenReturn(Optional.of(session));

        Assertions.assertAll(() -> currentGame.saveUserToActiveGame("1234", "admin"));
    }

    @Test
    void saveQuestionsToActiveGame() {
        CurrentGameSession session = createMockSession();
        when(repository.lockByPin("1234")).thenReturn(Optional.of(session));

        List<QuestionSet> questionSets = new ArrayList<>();
        Assertions.assertAll(() -> currentGame.saveQuestionsToActiveGame("1234", questionSets));
    }

    @Test
    void getCurrentQuestionNo() {
        CurrentGameSession session = createMockSession();
        when(repository.findByPin("1234")).thenReturn(Optional.of(session));

        Assertions.assertEquals(5, currentGame.getCurrentQuestionNo("1234"));
    }

    @Test
    void getQuestionsOnAPin() {
        CurrentGameSession session = createMockSession();
        when(repository.findByPin("1234")).thenReturn(Optional.of(session));

        List<QuestionSet> questionSets = currentGame.getQuestionsOnAPin("1234");
        Assertions.assertEquals("Q1", questionSets.getFirst().getQuestion());
    }

    @Test
    void incrementQuestionCount() {
        CurrentGameSession session = createMockSession();
        when(repository.lockByPin("1234")).thenReturn(Optional.of(session));

        Assertions.assertAll(() -> currentGame.incrementQuestionCount("1234"));
    }

    @Test
    void findModerator() {
        CurrentGameSession session = createMockSession();
        when(repository.findByPin("1234")).thenReturn(Optional.of(session));

        Assertions.assertEquals("admin", currentGame.findModerator("1234"));
    }

    @Test
    void saveCurrentScore() {
        CurrentGameSession session = createMockSession();
        when(repository.lockByPin("1234")).thenReturn(Optional.of(session));

        Assertions.assertAll(() -> currentGame.saveCurrentScore("1234", "admin", 1));
        Assertions.assertAll(() -> currentGame.saveCurrentScore("1234", "test", 1));
    }

    @Test
    void getCurrentScore() {
        CurrentGameSession session = createMockSession();
        when(repository.findByPin("1234")).thenReturn(Optional.of(session));

        var scores = currentGame.getCurrentScore("1234");
        Assertions.assertEquals(5, scores.get("admin"));
    }

    @Test
    void markAnswered_onlyFirstAnswerPerQuestionCounts() {
        CurrentGameSession session = createMockSession();
        when(repository.lockByPin("1234")).thenReturn(Optional.of(session));

        Assertions.assertAll(
                () -> Assertions.assertTrue(currentGame.markAnswered("1234", "tester", 0)),
                () -> Assertions.assertFalse(currentGame.markAnswered("1234", "tester", 0)),
                () -> Assertions.assertTrue(currentGame.markAnswered("1234", "tester", 1)),
                () -> Assertions.assertTrue(currentGame.markAnswered("1234", "other", 1)));
    }

    @Test
    void startQuestion_snapshotsScoresOncePerQuestion() {
        CurrentGameSession session = createMockSession();
        when(repository.lockByPin("1234")).thenReturn(Optional.of(session));

        currentGame.startQuestion("1234", 2);
        session.setScoresMap(new java.util.HashMap<>(java.util.Map.of("admin", 900)));
        currentGame.startQuestion("1234", 2);

        Assertions.assertEquals(5, session.getPreviousScoresMap().get("admin"));
        currentGame.startQuestion("1234", 3);
        Assertions.assertEquals(900, session.getPreviousScoresMap().get("admin"));
    }
}
