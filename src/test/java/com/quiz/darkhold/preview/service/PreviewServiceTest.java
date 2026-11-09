package com.quiz.darkhold.preview.service;

import com.quiz.darkhold.challenge.entity.Challenge;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.game.entity.Game;
import com.quiz.darkhold.game.repository.GameRepository;
import com.quiz.darkhold.init.GameConfig;
import com.quiz.darkhold.preview.repository.CurrentGame;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class PreviewServiceTest {
    private final ChallengeRepository challengeRepository = Mockito.mock(ChallengeRepository.class);
    private final GameRepository gameRepository = Mockito.mock(GameRepository.class);
    private final CurrentGame currentGame = Mockito.mock(CurrentGame.class);
    private final GameConfig gameConfig = Mockito.mock(GameConfig.class);
    private PreviewService previewService;

    @BeforeEach
    void setUp() {
        when(gameConfig.getPinLength()).thenReturn(5);
        previewService = new PreviewService(
                challengeRepository,
                gameRepository,
                currentGame,
                gameConfig);
    }

    @Test
    void fetchQuestions() {
        signIn("USER");
        when(challengeRepository.findById(anyLong())).thenReturn(mockOptionalChallenge());
        var challengeId = "1234";
        var previewInfo = previewService.fetchQuestions(challengeId);
        Assertions.assertEquals(challengeId, previewInfo.getChallengeId());
    }

    @Test
    void fetchQuestionsRejectsAnotherUser() {
        signIn("SOMEONE_ELSE");
        when(challengeRepository.findById(anyLong())).thenReturn(mockOptionalChallenge());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> previewService.fetchQuestions("1234"));
    }

    @Test
    void fetchQuestionsAllowsAdminOnAnotherUsersChallenge() {
        var admin = new com.quiz.darkhold.user.entity.User();
        admin.setEmail("ADMIN_USER");
        var details = new DarkholdUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                details, "n/a", List.of(new SimpleGrantedAuthority("ADMIN"))));
        when(challengeRepository.findById(anyLong())).thenReturn(mockOptionalChallenge());

        Assertions.assertEquals("hello", previewService.fetchQuestions("1234").getChallengeName());
    }

    @Test
    void fetchQuestionsFromPin() {
        var challengeId = "1234";
        when(gameRepository.findByPin(anyString())).thenReturn(mockGame(challengeId));
        when(challengeRepository.findById(anyLong())).thenReturn(mockOptionalChallenge());
        var previewInfo = previewService.fetchQuestionsFromPin(challengeId);
        Assertions.assertEquals(challengeId, previewInfo.getChallengeId());
    }

    @Test
    void generateQuizPin() {
        signIn("USER");
        var user = "USER";
        var game = mockGame("1234");
        when(gameRepository.existsByPin(anyString())).thenReturn(false);
        when(gameRepository.save(Mockito.any(Game.class))).thenReturn(game);
        when(gameRepository.findByPin(anyString())).thenReturn(game);
        when(challengeRepository.findById(anyLong())).thenReturn(mockOptionalChallenge());
        var publishInfo = previewService.generateQuizPin("1234", user);
        Assertions.assertEquals(user, publishInfo.getModerator());
    }

    @Test
    void getActiveChallenge() {
        var challengeId = "1234";
        when(gameRepository.findByGameStatusNot(anyString())).thenReturn(mockGames(challengeId));
        var publishInfo = previewService.getActiveChallenge();
        Assertions.assertEquals(challengeId, publishInfo.getPin());
    }

    private void signIn(final String email) {
        var user = new com.quiz.darkhold.user.entity.User();
        user.setEmail(email);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new DarkholdUserDetails(user), "n/a"));
    }

    private Optional<Challenge> mockOptionalChallenge() {
        return Optional.of(mockChallenge());
    }

    private Challenge mockChallenge() {
        var challenge = new Challenge();
        var owner = new com.quiz.darkhold.user.entity.User();
        owner.setEmail("USER");
        challenge.setOwner(owner);
        challenge.setQuestionSets(new ArrayList<>());
        challenge.setTitle("hello");
        return challenge;
    }

    private Game mockGame(final String challengeId) {
        var game = new Game();
        game.setChallengeId(challengeId);
        game.setPin("12345");
        return game;
    }

    private List<Game> mockGames(final String challengeId) {
        var game = new Game();
        game.setPin(challengeId);
        return List.of(game);
    }
}