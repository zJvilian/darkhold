package com.quiz.darkhold.practice.service;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.game.entity.Game;
import com.quiz.darkhold.game.entity.GameStatus;
import com.quiz.darkhold.game.model.GameMode;
import com.quiz.darkhold.game.repository.GameRepository;
import com.quiz.darkhold.preview.entity.CurrentGameSession;
import com.quiz.darkhold.preview.model.PublishInfo;
import com.quiz.darkhold.preview.repository.CurrentGameSessionRepository;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.User;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Service for handling practice/single-player game mode.
 */
@Service
public class PracticeService {

    private final Logger logger = LogManager.getLogger(PracticeService.class);

    private final ChallengeRepository challengeRepository;
    private final CurrentGameSessionRepository sessionRepository;
    private final GameRepository gameRepository;

    public PracticeService(final ChallengeRepository challengeRepository,
                          final CurrentGameSessionRepository sessionRepository,
                          final GameRepository gameRepository) {
        this.challengeRepository = challengeRepository;
        this.sessionRepository = sessionRepository;
        this.gameRepository = gameRepository;
    }

    /**
     * Initialize a practice game session.
     *
     * @param challengeId the challenge to practice
     * @param playerName  the player's name
     * @param session     HTTP session to store the practice game identifier
     * @return PublishInfo with practice game details
     */
    public PublishInfo initializePracticeGame(final String challengeId,
                                             final String playerName,
                                             final HttpSession session) {
        logger.info("Initializing practice game for challenge: {} player: {}", challengeId, playerName);

        // Internal identifier; not numeric, so nobody can join it, and short enough for the stored result
        String practiceId = "P-" + UUID.randomUUID().toString().substring(0, 8);

        // Fetch challenge questions
        Long challengeIdLong = Long.valueOf(challengeId);
        com.quiz.darkhold.challenge.entity.Challenge challenge = challengeRepository.findById(challengeIdLong)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found: " + challengeId));

        // same rule as the preview: owners, and admins and game managers for every quiz
        boolean owns = challenge.getOwner() != null && challenge.getOwner().getEmail().equals(currentUserEmail());
        if (!owns && !currentUserManagesAll()) {
            throw new IllegalArgumentException("Quiz not found: " + challengeId);
        }
        if (challenge.getQuestionSets() == null || challenge.getQuestionSets().isEmpty()) {
            throw new IllegalArgumentException("Quiz has no questions: " + challengeId);
        }

        List<QuestionSet> questionsList = new ArrayList<>(challenge.getQuestionSets());

        // Create practice game session
        CurrentGameSession gameSession = new CurrentGameSession(practiceId, playerName);
        gameSession.setGameMode(GameMode.PRACTICE);

        // In practice mode, player is both moderator and participant
        List<String> users = new ArrayList<>();
        users.add(playerName);
        gameSession.setUsersList(users);

        gameSession.setQuestionsList(questionsList);
        gameSession.setCurrentQuestionNo(0);
        gameSession.setScoresMap(new HashMap<>());
        gameSession.setStreakMap(new HashMap<>());
        gameSession.setGameStatus(com.quiz.darkhold.game.model.GameStatus.ACTIVE);

        // Save to database; the game row lets the finished run be stored under Past Games
        sessionRepository.save(gameSession);
        saveGame(practiceId, challengeId, playerName);

        // Store practice ID in session (treated as "gamePin" for compatibility)
        session.setAttribute("gamePin", practiceId);

        logger.info("Practice game initialized with ID: {}", practiceId);

        // Return publish info
        PublishInfo publishInfo = new PublishInfo();
        publishInfo.setPin(practiceId);
        publishInfo.setModerator(playerName);

        return publishInfo;
    }

    private void saveGame(final String pin, final String challengeId, final String playerName) {
        Game game = new Game();
        game.setPin(pin);
        game.setGameStatus(GameStatus.STARTED.name());
        game.setGameMode(GameMode.PRACTICE.name());
        game.setChallengeId(challengeId);
        game.setModerator(playerName);
        game.setCreatedOn(LocalDateTime.now(ZoneId.systemDefault()));
        gameRepository.save(game);
    }

    private static boolean currentUserManagesAll() {
        org.springframework.security.core.Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> User.CHALLENGE_MANAGER_ROLES.contains(a.getAuthority()));
    }

    private String currentUserEmail() {
        org.springframework.security.core.Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DarkholdUserDetails details) {
            return details.getEmail();
        }
        return null;
    }
}
