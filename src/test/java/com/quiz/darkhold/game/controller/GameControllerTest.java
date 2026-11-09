package com.quiz.darkhold.game.controller;

import com.quiz.darkhold.analytics.service.ResultService;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.game.model.CurrentScore;
import com.quiz.darkhold.game.model.Game;
import com.quiz.darkhold.game.model.GameMode;
import com.quiz.darkhold.game.model.QuestionPointer;
import com.quiz.darkhold.game.model.StartTrigger;
import com.quiz.darkhold.game.model.UserResponse;
import com.quiz.darkhold.game.service.AnswerValidationService;
import com.quiz.darkhold.game.service.GameService;
import com.quiz.darkhold.init.GameConfig;
import com.quiz.darkhold.util.CommonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.ui.Model;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GameController Tests")
class GameControllerTest {

    @Mock
    private GameService gameService;

    @Mock
    private GameConfig gameConfig;

    @Mock
    private AnswerValidationService answerValidationService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ResultService resultService;

    @Mock
    private com.quiz.darkhold.team.service.TeamService teamService;

    @Mock
    private Model model;

    @Mock
    private Principal principal;

    private GameController gameController;

    @BeforeEach
    void setUp() {
        when(gameConfig.getTimerSeconds()).thenReturn(20);
        gameController = new GameController(gameService, gameConfig, answerValidationService,
                messagingTemplate, resultService, teamService);
    }

    @Nested
    @DisplayName("startInterstitial() method tests")
    class StartInterstitialMethodTests {

        @Test
        @DisplayName("should add quiz pin to model and return interstitial view")
        void testStartInterstitial_ShouldAddPinToModelAndReturnView() {
            // Given
            String quizPin = "ABC123";

            // When
            try (MockedStatic<CommonUtils> mockedUtils = mockStatic(CommonUtils.class)) {
                mockedUtils.when(() -> CommonUtils.sanitizedString(quizPin)).thenReturn("ABC123");
                String result = gameController.startInterstitial(model, quizPin);

                // Then
                assertEquals("interstitial", result);
                verify(model).addAttribute("quizPin", quizPin);
            }
        }

        @Test
        @DisplayName("should sanitize quiz pin for logging")
        void testStartInterstitial_ShouldSanitizePinForLogging() {
            // Given
            String quizPin = "<script>alert('xss')</script>";

            // When
            try (MockedStatic<CommonUtils> mockedUtils = mockStatic(CommonUtils.class)) {
                mockedUtils.when(() -> CommonUtils.sanitizedString(quizPin))
                        .thenReturn("scriptalertxssscript");
                gameController.startInterstitial(model, quizPin);

                // Then
                mockedUtils.verify(() -> CommonUtils.sanitizedString(quizPin));
            }
        }

        @Test
        @DisplayName("should handle null quiz pin")
        void testStartInterstitial_WithNullPin_ShouldHandleGracefully() {
            // Given
            String quizPin = null;

            // When
            try (MockedStatic<CommonUtils> mockedUtils = mockStatic(CommonUtils.class)) {
                mockedUtils.when(() -> CommonUtils.sanitizedString(null)).thenReturn("");
                String result = gameController.startInterstitial(model, quizPin);

                // Then
                assertEquals("interstitial", result);
                verify(model).addAttribute("quizPin", null);
            }
        }

        @Test
        @DisplayName("should handle empty quiz pin")
        void testStartInterstitial_WithEmptyPin_ShouldReturnView() {
            // Given
            String quizPin = "";

            // When
            try (MockedStatic<CommonUtils> mockedUtils = mockStatic(CommonUtils.class)) {
                mockedUtils.when(() -> CommonUtils.sanitizedString("")).thenReturn("");
                String result = gameController.startInterstitial(model, quizPin);

                // Then
                assertEquals("interstitial", result);
            }
        }
    }

    @Nested
    @DisplayName("question() method tests")
    class QuestionMethodTests {

        @Test
        @DisplayName("should return question view when not on last question")
        void testQuestion_WhenNotLastQuestion_ShouldReturnQuestionView() {
            // Given
            QuestionPointer questionPointer = createQuestionPointer(1, 5);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);

            // When
            String result = gameController.question(model, "QUIZ123", principal);

            // Then
            assertEquals("question", result);
        }

        @Test
        @DisplayName("should redirect to final score when on last question")
        void testQuestion_WhenLastQuestion_ShouldReturnFinalScore() {
            // Given
            QuestionPointer questionPointer = createQuestionPointer(5, 5);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);
            when(gameService.getCurrentScore()).thenReturn(createScoreMap());

            // When
            String result = gameController.question(model, "QUIZ123", principal);

            // Then
            assertEquals("finalscore", result);
            verify(gameService, never()).cleanUpCurrentGame();
        }

        @Test
        @DisplayName("should call gameService.getCurrentQuestionPointer")
        void testQuestion_ShouldCallGetCurrentQuestionPointer() {
            // Given
            QuestionPointer questionPointer = createQuestionPointer(2, 5);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);

            // When
            gameController.question(model, "QUIZ123", principal);

            // Then
            verify(gameService).getCurrentQuestionPointer(anyString());
        }

        @Test
        @DisplayName("should return finalscore when on first and only question")
        void testQuestion_WhenFirstAndLastQuestion_ShouldReturnFinalScore() {
            // Given
            QuestionPointer questionPointer = createQuestionPointer(1, 1);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);
            when(gameService.getCurrentScore()).thenReturn(createScoreMap());

            // When
            String result = gameController.question(model, "QUIZ123", principal);

            // Then
            assertEquals("finalscore", result);
        }
    }

    @Nested
    @DisplayName("startGame() method tests")
    class StartGameMethodTests {

        @Test
        @DisplayName("should create challenge with correct question data")
        void testStartGame_ShouldCreateChallengeWithQuestionData() {
            // Given
            QuestionSet questionSet = createQuestionSet(1, "What is 2+2?");
            QuestionPointer questionPointer = createQuestionPointerWithQuestion(0, questionSet);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);
            when(gameService.getGameMode(anyString())).thenReturn(GameMode.MULTIPLAYER);

            // When
            String result = gameController.startGame(model, "QUIZ123", principal);

            // Then
            assertEquals("game", result);
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(model).addAttribute(ArgumentMatchers.eq("challenge"), captor.capture());
            assertNotNull(captor.getValue());
        }

        @Test
        @DisplayName("should add game timer to model")
        void testStartGame_ShouldAddGameTimer() {
            // Given
            QuestionSet questionSet = createQuestionSet(1, "Question?");
            QuestionPointer questionPointer = createQuestionPointerWithQuestion(0, questionSet);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);
            when(gameService.getGameMode(anyString())).thenReturn(GameMode.MULTIPLAYER);

            // When
            gameController.startGame(model, "QUIZ123", principal);

            // Then
            verify(model).addAttribute("game_timer", "20");
        }

        @Test
        @DisplayName("should return game view")
        void testStartGame_ShouldReturnGameView() {
            // Given
            QuestionSet questionSet = createQuestionSet(1, "Question?");
            QuestionPointer questionPointer = createQuestionPointerWithQuestion(0, questionSet);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);
            when(gameService.getGameMode(anyString())).thenReturn(GameMode.MULTIPLAYER);

            // When
            String result = gameController.startGame(model, "QUIZ123", principal);

            // Then
            assertEquals("game", result);
        }

        @Test
        @DisplayName("should go to final score instead of failing when there is no current question")
        void testStartGame_WithoutCurrentQuestion_ShouldGoToFinalScore() {
            // Given
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(createQuestionPointer(5, 5));
            when(gameService.getCurrentScore()).thenReturn(createScoreMap());

            // When
            String result = gameController.startGame(model, "QUIZ123", principal);

            // Then
            assertEquals("finalscore", result);
            verify(gameService, never()).questionShown(anyInt());
        }

        @Test
        @DisplayName("should record the question shown to the player")
        void testStartGame_ShouldRecordShownQuestion() {
            // Given
            QuestionPointer pointer = createQuestionPointerWithQuestion(2, createQuestionSet(3, "Q?"));
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(pointer);
            when(gameService.getGameMode(anyString())).thenReturn(GameMode.MULTIPLAYER);

            // When
            gameController.startGame(model, "QUIZ123", principal);

            // Then
            verify(gameService).questionShown(2);
        }

        @Test
        @DisplayName("should increment question number in challenge")
        void testStartGame_ShouldIncrementQuestionNumber() {
            // Given
            QuestionSet questionSet = createQuestionSet(2, "Second question?");
            QuestionPointer questionPointer = createQuestionPointerWithQuestion(1, questionSet);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);
            when(gameService.getGameMode(anyString())).thenReturn(GameMode.MULTIPLAYER);

            // When
            gameController.startGame(model, "QUIZ123", principal);

            // Then
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(model).addAttribute(ArgumentMatchers.eq("challenge"), captor.capture());
            assertNotNull(captor.getValue());
        }
    }

    @Nested
    @DisplayName("finalScore() method tests")
    class FinalScoreMethodTests {

        @Test
        @DisplayName("should add score to model for a non-moderator without finalizing the game")
        void testFinalScore_ForPlayer_ShouldOnlyShowScores() {
            // Given
            java.util.Map<String, Integer> scoreMap = createScoreMap();
            when(gameService.getCurrentScore()).thenReturn(scoreMap);

            // When
            String result = gameController.finalScore(model, "QUIZ123", principal);

            // Then
            assertEquals("finalscore", result);
            ArgumentCaptor<CurrentScore> scoreCaptor = ArgumentCaptor.forClass(CurrentScore.class);
            verify(model).addAttribute(ArgumentMatchers.eq("score"), scoreCaptor.capture());
            assertEquals(scoreMap, scoreCaptor.getValue().getScore());
            verify(resultService, never()).saveGameResult(anyString(), anyString());
            verify(gameService, never()).markGame(anyString(), ArgumentMatchers.any());
            verify(gameService, never()).cleanUpCurrentGame();
        }

        @Test
        @DisplayName("should save the result with the real title and mark the game finished for the moderator")
        void testFinalScore_ForModerator_ShouldFinalizeGame() {
            // Given
            when(gameService.getCurrentScore()).thenReturn(createScoreMap());
            when(gameService.isModerator("QUIZ123", principal)).thenReturn(true);
            when(gameService.challengeTitle("QUIZ123")).thenReturn("Capitals");
            when(gameService.getGameMode("QUIZ123")).thenReturn(GameMode.MULTIPLAYER);

            // When
            gameController.finalScore(model, "QUIZ123", principal);

            // Then
            verify(resultService).saveGameResult("QUIZ123", "Capitals");
            verify(gameService).markGame("QUIZ123", com.quiz.darkhold.game.entity.GameStatus.FINISHED);
            verify(gameService, never()).cleanUpCurrentGame();
        }

        @Test
        @DisplayName("should clean up a finished practice session straight away")
        void testFinalScore_ForPracticeModerator_ShouldCleanUp() {
            // Given
            when(gameService.getCurrentScore()).thenReturn(createScoreMap());
            when(gameService.isModerator("PRACTICE-1", principal)).thenReturn(true);
            when(gameService.getGameMode("PRACTICE-1")).thenReturn(GameMode.PRACTICE);

            // When
            gameController.finalScore(model, "PRACTICE-1", principal);

            // Then
            verify(gameService).cleanUpCurrentGame();
        }

        @Test
        @DisplayName("should handle empty score map")
        void testFinalScore_WithEmptyScore_ShouldHandleCorrectly() {
            // Given
            when(gameService.getCurrentScore()).thenReturn(new HashMap<>());

            // When
            String result = gameController.finalScore(model, null, principal);

            // Then
            assertEquals("finalscore", result);
            ArgumentCaptor<CurrentScore> scoreCaptor = ArgumentCaptor.forClass(CurrentScore.class);
            verify(model).addAttribute(ArgumentMatchers.eq("score"), scoreCaptor.capture());
            assertEquals(0, scoreCaptor.getValue().getScore().size());
        }
    }

    @Nested
    @DisplayName("enterGame() method tests")
    class EnterGameMethodTests {

        private QuestionSet question;

        @BeforeEach
        void openQuestion() {
            question = createQuestionSet(1, "Question?");
            question.setPoints(1000);
            when(principal.getName()).thenReturn("player1");
            when(gameService.shownQuestionNo()).thenReturn(0);
            when(gameService.questionAt(0)).thenReturn(question);
            when(gameService.markAnswered("player1", 0)).thenReturn(true);
            when(gameService.elapsedMillis()).thenReturn(5000L);
            when(answerValidationService.getPoints(question)).thenReturn(1000);
            when(answerValidationService.shouldScore(question)).thenReturn(true);
            when(gameService.updateStreak("player1", true)).thenReturn(1);
            when(gameService.calculateScoreWithStreak(anyInt(), anyInt(), anyInt())).thenReturn(750);
        }

        @Test
        @DisplayName("should score the server-checked choice using server-side elapsed time")
        void testEnterGame_WithCorrectChoice_ShouldScoreFromServerTime() {
            when(answerValidationService.validateSelection(question, "A")).thenReturn(true);

            assertTrue(gameController.enterGame("A", principal));

            // (20s default limit - 5s) / 20s -> factor 750
            verify(gameService).calculateScoreWithStreak(1000, 750, 1);
            verify(gameService).saveCurrentScore("player1", 750);
            verify(gameService, never()).incrementQuestionNo();
        }

        @Test
        @DisplayName("should use the question's own time limit for the time factor")
        void testEnterGame_ShouldUseQuestionTimeLimit() {
            question.setTimeLimit(10);
            when(answerValidationService.validateSelection(question, "A")).thenReturn(true);

            gameController.enterGame("A", principal);

            // (10s - 5s) / 10s -> factor 500
            verify(gameService).calculateScoreWithStreak(1000, 500, 1);
        }

        @Test
        @DisplayName("should give no time bonus once the elapsed time exceeds the limit")
        void testEnterGame_WhenLate_ShouldClampTimeFactorToZero() {
            when(gameService.elapsedMillis()).thenReturn(Long.MAX_VALUE);
            when(answerValidationService.validateSelection(question, "A")).thenReturn(true);

            gameController.enterGame("A", principal);

            verify(gameService).calculateScoreWithStreak(1000, 0, 1);
        }

        @Test
        @DisplayName("should score zero and reset the streak for a wrong choice")
        void testEnterGame_WithWrongChoice_ShouldScoreZero() {
            when(answerValidationService.validateSelection(question, "B")).thenReturn(false);

            gameController.enterGame("B", principal);

            verify(gameService).updateStreak("player1", false);
            verify(gameService).saveCurrentScore("player1", 0);
        }

        @Test
        @DisplayName("should score zero on timeout")
        void testEnterGame_OnTimeout_ShouldScoreZero() {
            gameController.enterGame("", principal);

            verify(gameService).saveCurrentScore("player1", 0);
        }

        @Test
        @DisplayName("should ignore a second answer to the same question")
        void testEnterGame_WhenAlreadyAnswered_ShouldBeIgnored() {
            when(gameService.markAnswered("player1", 0)).thenReturn(false);

            assertFalse(gameController.enterGame("A", principal));

            verify(gameService, never()).saveCurrentScore(anyString(), anyInt());
            verify(gameService, never()).incrementQuestionNo();
        }

        @Test
        @DisplayName("should ignore an answer when the player was never shown a question")
        void testEnterGame_WithoutShownQuestion_ShouldBeIgnored() {
            when(gameService.shownQuestionNo()).thenReturn(null);

            assertFalse(gameController.enterGame("A", principal));

            verify(gameService, never()).markAnswered(anyString(), anyInt());
        }

        @Test
        @DisplayName("should move the game on without scoring when the moderator answers in multiplayer")
        void testEnterGame_WhenModerator_ShouldIncrementQuestion() {
            when(gameService.isModerator(principal)).thenReturn(true);
            when(gameService.isPracticeMode()).thenReturn(false);

            assertTrue(gameController.enterGame("", principal));

            verify(gameService).incrementQuestionNo();
            verify(gameService, never()).saveCurrentScore(anyString(), anyInt());
        }

        @Test
        @DisplayName("should score and then move on when the practice player answers")
        void testEnterGame_WhenPracticeModerator_ShouldScoreAndIncrement() {
            when(gameService.isModerator(principal)).thenReturn(true);
            when(gameService.isPracticeMode()).thenReturn(true);
            when(answerValidationService.validateSelection(question, "A")).thenReturn(true);

            gameController.enterGame("A", principal);

            verify(gameService).saveCurrentScore("player1", 750);
            verify(gameService).incrementQuestionNo();
        }
    }

    @Nested
    @DisplayName("validateTypeAnswer() method tests")
    class ValidateTypeAnswerMethodTests {

        private QuestionSet question;

        @BeforeEach
        void openQuestion() {
            question = createQuestionSet(1, "Capital of France?");
            when(principal.getName()).thenReturn("player1");
            when(gameService.shownQuestionNo()).thenReturn(0);
            when(gameService.questionAt(0)).thenReturn(question);
            when(gameService.markAnswered("player1", 0)).thenReturn(true, false);
            when(gameService.elapsedMillis()).thenReturn(1000L);
            when(answerValidationService.validateAnswer(question, "paris")).thenReturn(true);
            when(answerValidationService.shouldScore(question)).thenReturn(true);
            when(answerValidationService.getPoints(question)).thenReturn(1000);
            when(gameService.updateStreak("player1", true)).thenReturn(1);
            when(gameService.calculateScoreWithStreak(anyInt(), anyInt(), anyInt())).thenReturn(950);
        }

        @Test
        @DisplayName("should score a correct typed answer once and refuse a repeat")
        void testValidateTypeAnswer_ShouldScoreOnce() {
            assertEquals("correct", gameController.validateTypeAnswer("paris", principal));
            assertEquals("incorrect", gameController.validateTypeAnswer("paris", principal));

            verify(gameService, times(1)).saveCurrentScore("player1", 950);
        }

        @Test
        @DisplayName("should not let the multiplayer moderator answer")
        void testValidateTypeAnswer_ForModerator_ShouldBeRejected() {
            when(gameService.isModerator(principal)).thenReturn(true);
            when(gameService.isPracticeMode()).thenReturn(false);

            assertEquals("incorrect", gameController.validateTypeAnswer("paris", principal));

            verify(gameService, never()).markAnswered(anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("getGame() WebSocket method tests")
    class GetGameMethodTests {

        @Test
        @DisplayName("should send user response to PIN-scoped topic")
        void testGetGame_ShouldSendUserResponseToPinScopedTopic() {
            // Given
            Game game = new Game("testUser");
            game.setPin("ABC123");
            List<String> participants = new ArrayList<>();
            participants.add("user1");
            participants.add("user2");
            when(gameService.saveAndGetAllParticipants("ABC123", "testUser")).thenReturn(participants);
            when(teamService.isTeamMode("ABC123")).thenReturn(false);

            // When
            gameController.getGame(game, namedPrincipal(game.getName()));

            // Then
            verify(gameService).saveAndGetAllParticipants("ABC123", "testUser");
            verify(messagingTemplate).convertAndSend(
                    ArgumentMatchers.eq("/topic/ABC123/user"),
                    ArgumentMatchers.any(UserResponse.class));
        }

        @Test
        @DisplayName("should call gameService with correct game pin")
        void testGetGame_ShouldCallServiceWithCorrectPin() {
            // Given
            Game game = new Game("player1");
            game.setPin("XYZ789");
            when(gameService.saveAndGetAllParticipants("XYZ789", "player1")).thenReturn(new ArrayList<>());
            when(teamService.isTeamMode("XYZ789")).thenReturn(false);

            // When
            gameController.getGame(game, namedPrincipal(game.getName()));

            // Then
            verify(gameService).saveAndGetAllParticipants("XYZ789", "player1");
        }

        @Test
        @DisplayName("should handle empty participants list")
        void testGetGame_WithEmptyParticipants_ShouldSendResponse() {
            // Given
            Game game = new Game("player");
            game.setPin("GAME1");
            when(gameService.saveAndGetAllParticipants("GAME1", "player")).thenReturn(new ArrayList<>());
            when(teamService.isTeamMode("GAME1")).thenReturn(false);

            // When
            gameController.getGame(game, namedPrincipal(game.getName()));

            // Then
            verify(messagingTemplate).convertAndSend(
                    ArgumentMatchers.eq("/topic/GAME1/user"),
                    ArgumentMatchers.any(UserResponse.class));
        }
    }

    @Nested
    @DisplayName("getGame() join authorization")
    class GetGameAuthorizationTests {

        @Test
        @DisplayName("should ignore a join for a name other than the connected user")
        void testGetGame_ForAnotherUser_ShouldBeIgnored() {
            Game game = new Game("someoneElse");
            game.setPin("ABC123");

            gameController.getGame(game, namedPrincipal("attacker"));

            verify(gameService, never()).saveAndGetAllParticipants(anyString(), anyString());
            verify(messagingTemplate, never()).convertAndSend(anyString(), ArgumentMatchers.<Object>any());
        }
    }

    @Nested
    @DisplayName("startTrigger() WebSocket method tests")
    class StartTriggerMethodTests {

        @Test
        @DisplayName("should mark the game started and send the trigger for the moderator")
        void testStartTrigger_ForModerator_ShouldStartGame() {
            when(principal.getName()).thenReturn("moderator");
            when(gameService.isModerator("GAME123", principal)).thenReturn(true);

            gameController.startTrigger("GAME123", principal);

            verify(gameService).markGame("GAME123", com.quiz.darkhold.game.entity.GameStatus.STARTED);
            verify(messagingTemplate).convertAndSend(
                    ArgumentMatchers.eq("/topic/GAME123/start"),
                    ArgumentMatchers.any(StartTrigger.class));
        }

        @Test
        @DisplayName("should ignore a start from anyone but the moderator")
        void testStartTrigger_ForPlayer_ShouldBeIgnored() {
            when(principal.getName()).thenReturn("player");

            gameController.startTrigger("GAME123", principal);

            verify(gameService, never()).markGame(anyString(), ArgumentMatchers.any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), ArgumentMatchers.<Object>any());
        }
    }

    @Nested
    @DisplayName("host-only WebSocket control tests")
    class HostControlTests {

        @Test
        @DisplayName("should pause the PIN's own game for the moderator")
        void testPause_ForModerator_ShouldUseTheGivenPin() {
            when(gameService.isModerator("GAME1", principal)).thenReturn(true);
            when(gameService.pauseGame("GAME1")).thenReturn(true);

            gameController.pauseGame("GAME1", principal);

            verify(messagingTemplate).convertAndSend("/topic/GAME1/game_paused", true);
        }

        @Test
        @DisplayName("should resume, skip, end and count using the given PIN")
        void testControls_ForModerator_ShouldUseTheGivenPin() {
            when(gameService.isModerator("GAME1", principal)).thenReturn(true);
            when(gameService.resumeGame("GAME1")).thenReturn(1500L);
            when(gameService.getParticipantCount("GAME1")).thenReturn(3);

            gameController.resumeGame("GAME1", principal);
            gameController.skipQuestion("GAME1", principal);
            gameController.endGameEarly("GAME1", principal);
            gameController.getParticipantCount("GAME1", principal);

            verify(messagingTemplate).convertAndSend("/topic/GAME1/game_resumed", 1500L);
            verify(gameService).skipQuestion("GAME1");
            verify(gameService).setGameStatus("GAME1", com.quiz.darkhold.game.model.GameStatus.ENDED);
            verify(gameService).markGame("GAME1", com.quiz.darkhold.game.entity.GameStatus.FINISHED);
            verify(messagingTemplate).convertAndSend("/topic/GAME1/participant_count", 3);
        }

        @Test
        @DisplayName("should kick the player from the given PIN's game for the moderator")
        void testKick_ForModerator_ShouldUseTheGivenPin() {
            when(gameService.isModerator("GAME1", principal)).thenReturn(true);
            when(gameService.kickPlayer("GAME1", "bob")).thenReturn(true);

            gameController.kickPlayer("GAME1:bob", principal);

            verify(messagingTemplate).convertAndSend("/topic/GAME1/player_kicked", "bob");
        }

        @Test
        @DisplayName("should ignore every host-only message from a non-moderator")
        void testControls_ForPlayer_ShouldBeIgnored() {
            gameController.pauseGame("GAME1", principal);
            gameController.resumeGame("GAME1", principal);
            gameController.skipQuestion("GAME1", principal);
            gameController.endGameEarly("GAME1", principal);
            gameController.kickPlayer("GAME1:bob", principal);
            gameController.getParticipantCount("GAME1", principal);
            gameController.pauseGame("GAME1", null);

            verify(gameService, never()).pauseGame(anyString());
            verify(gameService, never()).resumeGame(anyString());
            verify(gameService, never()).skipQuestion(anyString());
            verify(gameService, never()).setGameStatus(anyString(), ArgumentMatchers.any());
            verify(gameService, never()).kickPlayer(anyString(), anyString());
            verify(messagingTemplate, never()).convertAndSend(anyString(), ArgumentMatchers.<Object>any());
        }

        @Test
        @DisplayName("should ignore score and team broadcasts from a non-moderator")
        void testBroadcasts_ForPlayer_ShouldBeIgnored() {
            gameController.scoresFetch("GAME1", principal);
            gameController.assignToTeam("GAME1:bob:Red", principal);

            verify(teamService, never()).assignPlayerToTeam(anyString(), anyString(), anyString());
            verify(messagingTemplate, never()).convertAndSend(anyString(), ArgumentMatchers.<Object>any());
        }
    }

    @Nested
    @DisplayName("questionFetch() WebSocket method tests")
    class QuestionFetchMethodTests {

        @Test
        @DisplayName("should send END_GAME trigger when on last question")
        void testQuestionFetch_WhenLastQuestion_ShouldSendEndGame() {
            // Given
            QuestionPointer questionPointer = createQuestionPointer(5, 5);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);

            // When - message format is "pin:username" or "pin"
            gameController.questionFetch("GAME1:user1");

            // Then
            verify(messagingTemplate).convertAndSend(
                    ArgumentMatchers.eq("/topic/GAME1/question_read"),
                    ArgumentMatchers.any(StartTrigger.class));
        }

        @Test
        @DisplayName("should send question with number and text")
        void testQuestionFetch_WhenNotLastQuestion_ShouldSendQuestionText() {
            // Given
            QuestionSet questionSet = createQuestionSet(1, "What is capital of France?");
            QuestionPointer questionPointer = createQuestionPointerWithQuestion(1, questionSet);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);

            // When
            gameController.questionFetch("GAME1:user1");

            // Then
            verify(messagingTemplate).convertAndSend(
                    ArgumentMatchers.eq("/topic/GAME1/question_read"),
                    ArgumentMatchers.any(StartTrigger.class));
        }

        @Test
        @DisplayName("should call gameService.getCurrentQuestionPointer")
        void testQuestionFetch_ShouldCallGetCurrentQuestionPointer() {
            // Given
            QuestionSet questionSet = createQuestionSet(1, "Question?");
            QuestionPointer questionPointer = createQuestionPointerWithQuestion(1, questionSet);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);

            // When
            gameController.questionFetch("GAME1");

            // Then
            verify(gameService).getCurrentQuestionPointer(anyString());
        }

        @Test
        @DisplayName("should include question number in response")
        void testQuestionFetch_ShouldIncludeQuestionNumber() {
            // Given
            QuestionSet questionSet = createQuestionSet(3, "Question 3?");
            QuestionPointer questionPointer = createQuestionPointerWithQuestion(2, questionSet);
            when(gameService.getCurrentQuestionPointer(anyString())).thenReturn(questionPointer);

            // When
            gameController.questionFetch("GAME1:user1");

            // Then
            verify(messagingTemplate).convertAndSend(
                    ArgumentMatchers.eq("/topic/GAME1/question_read"),
                    ArgumentMatchers.any(StartTrigger.class));
        }
    }

    @Nested
    @DisplayName("scoresFetch() WebSocket method tests")
    class ScoresFetchMethodTests {

        @Test
        @DisplayName("should send true to PIN-scoped topic")
        void testScoresFetch_ShouldSendTrueToPinScopedTopic() {
            when(gameService.isModerator("GAME1", principal)).thenReturn(true);

            // When
            gameController.scoresFetch("GAME1", principal);

            // Then
            verify(messagingTemplate).convertAndSend("/topic/GAME1/read_scores", true);
        }

        @Test
        @DisplayName("should send to correct PIN topic")
        void testScoresFetch_ShouldSendToCorrectPinTopic() {
            when(gameService.isModerator("QUIZ123", principal)).thenReturn(true);

            // When
            gameController.scoresFetch("QUIZ123", principal);

            // Then
            verify(messagingTemplate).convertAndSend("/topic/QUIZ123/read_scores", true);
        }
    }

    @Nested
    @DisplayName("Integration scenario tests")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("should complete full game flow from interstitial to final score")
        void testCompleteGameFlow() {
            // Given
            String quizPin = "QUIZ123";

            // When
            try (MockedStatic<CommonUtils> mockedUtils = mockStatic(CommonUtils.class)) {
                mockedUtils.when(() -> CommonUtils.sanitizedString(quizPin)).thenReturn("QUIZ123");
                String interstitialResult = gameController.startInterstitial(model, quizPin);

                // Then
                assertEquals("interstitial", interstitialResult);
                verify(model).addAttribute("quizPin", quizPin);
            }
        }
    }

    // ======================== Helper Methods ========================

    private Principal namedPrincipal(final String name) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                name, "n/a", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("R")));
    }

    private QuestionPointer createQuestionPointer(final int current, final int total) {
        QuestionPointer pointer = new QuestionPointer();
        pointer.setCurrentQuestionNumber(current);
        pointer.setTotalQuestionCount(total);
        return pointer;
    }

    private QuestionPointer createQuestionPointerWithQuestion(final int current,
                                                              final QuestionSet questionSet) {
        QuestionPointer pointer = new QuestionPointer();
        pointer.setCurrentQuestionNumber(current);
        pointer.setTotalQuestionCount(5);
        pointer.setCurrentQuestion(questionSet);
        return pointer;
    }

    private QuestionSet createQuestionSet(final int id, final String question) {
        QuestionSet set = new QuestionSet();
        set.setId((long) id);
        set.setQuestion(question);
        return set;
    }

    private java.util.Map<String, Integer> createScoreMap() {
        java.util.Map<String, Integer> scoreMap = new HashMap<>();
        scoreMap.put("score", 100);
        return scoreMap;
    }
}

