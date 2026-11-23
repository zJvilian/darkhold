package com.quiz.darkhold.home.service;

import com.quiz.darkhold.game.entity.GameStatus;
import com.quiz.darkhold.game.repository.GameRepository;
import com.quiz.darkhold.live.LiveRoomRepository;
import com.quiz.darkhold.preview.repository.CurrentGame;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HomeService {
    private final GameRepository gameRepository;

    private final CurrentGame currentGame;

    private final LiveRoomRepository liveRoomRepository;

    public HomeService(final GameRepository gameRepository, final CurrentGame currentGame,
                       final LiveRoomRepository liveRoomRepository) {
        this.gameRepository = gameRepository;
        this.currentGame = currentGame;
        this.liveRoomRepository = liveRoomRepository;
    }

    /**
     * Whether the PIN is the code of an open live quiz room.
     *
     * @param pin pin
     * @return true for a live room that has not expired
     */
    public boolean isLiveRoom(final String pin) {
        long now = System.currentTimeMillis();
        return pin != null && pin.matches("[0-9]{6}")
                && liveRoomRepository.findById(pin).filter(room -> room.getExpiresAt() > now).isPresent();
    }

    /**
     * Verifies whether the entered pin is valid or not.
     *
     * @param pin pin
     * @return true or false
     */
    public Boolean validateGamePin(final String pin) {
        com.quiz.darkhold.game.entity.Game game = gameRepository.findByPin(pin);
        if (game != null && GameStatus.WAITING.name().equalsIgnoreCase(game.getGameStatus())) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public List<String> participantsInActiveQuiz(final String pin) {
        return currentGame.getActiveUsersInGame(pin);
    }

    /**
     * Get the moderator of the game.
     *
     * @param pin game pin
     * @return moderator username
     */
    public String getModerator(final String pin) {
        return currentGame.findModerator(pin);
    }

    /**
     * Check if a game is in team mode.
     *
     * @param pin game pin
     * @return true if team mode is enabled
     */
    public boolean isTeamMode(final String pin) {
        com.quiz.darkhold.game.entity.Game game = gameRepository.findByPin(pin);
        return game != null && Boolean.TRUE.equals(game.getTeamMode());
    }
}
