package com.quiz.darkhold.practice.service;

import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.game.entity.Game;
import com.quiz.darkhold.game.repository.GameRepository;
import com.quiz.darkhold.preview.entity.CurrentGameSession;
import com.quiz.darkhold.preview.model.PublishInfo;
import com.quiz.darkhold.preview.repository.CurrentGameSessionRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeService Tests")
class PracticeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private CurrentGameSessionRepository sessionRepository;

    @Mock
    private HttpSession httpSession;

    @Mock
    private GameRepository gameRepository;

    private PracticeService practiceService;

    @BeforeEach
    void setUp() {
        practiceService = new PracticeService(challengeRepository, sessionRepository, gameRepository);
        signIn("player1");
    }

    private void signIn(final String email) {
        var user = new com.quiz.darkhold.user.entity.User();
        user.setEmail(email);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new DarkholdUserDetails(user), "n/a"));
    }

    @Nested
    @DisplayName("initializePracticeGame tests")
    class InitializePracticeGameTests {

        @Test
        @DisplayName("Should reject a challenge owned by another user")
        void shouldRejectForeignChallenge() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));
            signIn("someoneElse");

            assertThrows(IllegalArgumentException.class,
                    () -> practiceService.initializePracticeGame("1", "player1", httpSession));
            verify(sessionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("Should return a non-numeric pin that fits the stored result")
        void shouldReturnPublishInfoWithPracticePrefixedPin() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            PublishInfo result = practiceService.initializePracticeGame("1", "player1", httpSession);

            assertNotNull(result);
            assertTrue(result.getPin().startsWith("P-"));
            assertTrue(result.getPin().length() <= 10);
        }

        @Test
        @DisplayName("Should store a practice game so the run can be saved under Past Games")
        void shouldPersistPracticeGame() {
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(createTestChallenge()));

            PublishInfo result = practiceService.initializePracticeGame("1", "player1", httpSession);

            ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
            verify(gameRepository).save(captor.capture());
            assertEquals(result.getPin(), captor.getValue().getPin());
            assertEquals("PRACTICE", captor.getValue().getGameMode());
            assertEquals("player1", captor.getValue().getModerator());
            assertEquals("1", captor.getValue().getChallengeId());
            assertNotNull(captor.getValue().getCreatedOn());
        }

        @Test
        @DisplayName("Should let admins and game managers practice any quiz")
        void shouldAllowManagersToPracticeForeignQuiz() {
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(createTestChallenge()));
            var manager = new com.quiz.darkhold.user.entity.User();
            manager.setEmail("manager");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new DarkholdUserDetails(manager), "n/a",
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("GAME_MANAGER"))));

            assertNotNull(practiceService.initializePracticeGame("1", "manager", httpSession));
        }

        @Test
        @DisplayName("Should reject a quiz without questions")
        void shouldRejectEmptyQuiz() {
            Challenge challenge = createTestChallenge();
            challenge.setQuestionSets(new ArrayList<>());
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            assertThrows(IllegalArgumentException.class,
                    () -> practiceService.initializePracticeGame("1", "player1", httpSession));
            verify(gameRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("Should set moderator to playerName in PublishInfo")
        void shouldSetModeratorToPlayerName() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            PublishInfo result = practiceService.initializePracticeGame("1", "player1", httpSession);

            assertEquals("player1", result.getModerator());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when challenge not found")
        void shouldThrowWhenChallengeNotFound() {
            when(challengeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> practiceService.initializePracticeGame("999", "player1", httpSession));
        }

        @Test
        @DisplayName("Should save session with GameMode PRACTICE")
        void shouldSaveSessionWithPracticeGameMode() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            practiceService.initializePracticeGame("1", "player1", httpSession);

            ArgumentCaptor<CurrentGameSession> captor = ArgumentCaptor.forClass(CurrentGameSession.class);
            verify(sessionRepository).save(captor.capture());
            assertEquals(com.quiz.darkhold.game.model.GameMode.PRACTICE, captor.getValue().getGameMode());
        }

        @Test
        @DisplayName("Should set users list with playerName")
        void shouldSetUsersListWithPlayerName() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            practiceService.initializePracticeGame("1", "player1", httpSession);

            ArgumentCaptor<CurrentGameSession> captor = ArgumentCaptor.forClass(CurrentGameSession.class);
            verify(sessionRepository).save(captor.capture());
            List<String> users = captor.getValue().getUsersList();
            assertNotNull(users);
            assertEquals(1, users.size());
            assertEquals("player1", users.get(0));
        }

        @Test
        @DisplayName("Should set currentQuestionNo to 0")
        void shouldSetCurrentQuestionNoToZero() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            practiceService.initializePracticeGame("1", "player1", httpSession);

            ArgumentCaptor<CurrentGameSession> captor = ArgumentCaptor.forClass(CurrentGameSession.class);
            verify(sessionRepository).save(captor.capture());
            assertEquals(0, captor.getValue().getCurrentQuestionNo());
        }

        @Test
        @DisplayName("Should set gameStatus to ACTIVE")
        void shouldSetGameStatusToActive() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            practiceService.initializePracticeGame("1", "player1", httpSession);

            ArgumentCaptor<CurrentGameSession> captor = ArgumentCaptor.forClass(CurrentGameSession.class);
            verify(sessionRepository).save(captor.capture());
            assertEquals(com.quiz.darkhold.game.model.GameStatus.ACTIVE, captor.getValue().getGameStatus());
        }

        @Test
        @DisplayName("Should store practiceId in HttpSession as gamePin")
        void shouldStorePracticeIdInHttpSession() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            PublishInfo result = practiceService.initializePracticeGame("1", "player1", httpSession);

            verify(httpSession).setAttribute(eq("gamePin"), eq(result.getPin()));
        }

        @Test
        @DisplayName("Should load questions from challenge")
        void shouldLoadQuestionsFromChallenge() {
            Challenge challenge = createTestChallenge();
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

            practiceService.initializePracticeGame("1", "player1", httpSession);

            ArgumentCaptor<CurrentGameSession> captor = ArgumentCaptor.forClass(CurrentGameSession.class);
            verify(sessionRepository).save(captor.capture());
            assertNotNull(captor.getValue().getQuestionsJson());
        }
    }

    // Helper methods

    private Challenge createTestChallenge() {
        Challenge challenge = new Challenge();
        challenge.setId(1L);
        challenge.setTitle("Test Challenge");
        challenge.setDescription("Test Description");
        com.quiz.darkhold.user.entity.User owner = new com.quiz.darkhold.user.entity.User();
        owner.setEmail("player1");
        challenge.setOwner(owner);
        QuestionSet qs = new QuestionSet();
        qs.setQuestion("What is 2+2?");
        qs.setAnswer1("3");
        qs.setAnswer2("4");
        qs.setAnswer3("5");
        qs.setAnswer4("6");
        challenge.setQuestionSets(new ArrayList<>(List.of(qs)));
        return challenge;
    }
}
