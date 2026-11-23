package com.quiz.darkhold.home.service;

import com.quiz.darkhold.game.entity.Game;
import com.quiz.darkhold.game.entity.GameStatus;
import com.quiz.darkhold.game.repository.GameRepository;
import com.quiz.darkhold.live.LiveRoom;
import com.quiz.darkhold.live.LiveRoomRepository;
import com.quiz.darkhold.preview.repository.CurrentGame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class HomeServiceTest {
    private final GameRepository gameRepository = Mockito.mock(GameRepository.class);
    private final CurrentGame currentGame = Mockito.mock(CurrentGame.class);
    private final LiveRoomRepository liveRoomRepository = Mockito.mock(LiveRoomRepository.class);
    private final HomeService homeService = new HomeService(gameRepository, currentGame, liveRoomRepository);

    @Test
    void isLiveRoomOnlyForOpenSixDigitRooms() {
        LiveRoom open = new LiveRoom();
        open.setExpiresAt(System.currentTimeMillis() + 60_000);
        LiveRoom expired = new LiveRoom();
        expired.setExpiresAt(System.currentTimeMillis() - 1);
        when(liveRoomRepository.findById("123456")).thenReturn(java.util.Optional.of(open));
        when(liveRoomRepository.findById("654321")).thenReturn(java.util.Optional.of(expired));
        Assertions.assertTrue(homeService.isLiveRoom("123456"));
        Assertions.assertFalse(homeService.isLiveRoom("654321"));
        Assertions.assertFalse(homeService.isLiveRoom("12345"));
        Assertions.assertFalse(homeService.isLiveRoom(null));
    }

    @Test
    void validateGamePinSuccess() {
        when(gameRepository.findByPin(anyString())).thenReturn(mockGame());
        Assertions.assertTrue(homeService.validateGamePin("1234"));
    }

    @Test
    void validateGamePinRejectsStartedGame() {
        var game = mockGame();
        game.setGameStatus(GameStatus.STARTED.name());
        when(gameRepository.findByPin(anyString())).thenReturn(game);
        Assertions.assertFalse(homeService.validateGamePin("1234"));
    }

    @Test
    void validateGamePinFailure() {
        when(gameRepository.findByPin(anyString())).thenReturn(null);
        Assertions.assertFalse(homeService.validateGamePin("1234"));
    }

    @Test
    void verifyParticipantsInActiveQuiz() {
        Assertions.assertNotNull(homeService.participantsInActiveQuiz("1234"));
    }

    private Game mockGame() {
        var game = new Game();
        game.setGameStatus(GameStatus.WAITING.name());
        return game;
    }

}