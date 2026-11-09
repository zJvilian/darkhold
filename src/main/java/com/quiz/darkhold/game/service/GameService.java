package com.quiz.darkhold.game.service;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.game.entity.Game;
import com.quiz.darkhold.game.model.GameMode;
import com.quiz.darkhold.game.model.GameStatus;
import com.quiz.darkhold.game.model.QuestionPointer;
import com.quiz.darkhold.game.model.ScoreResult;
import com.quiz.darkhold.game.repository.GameRepository;
import com.quiz.darkhold.preview.model.PublishInfo;
import com.quiz.darkhold.preview.repository.CurrentGame;
import com.quiz.darkhold.preview.service.PreviewService;
import com.quiz.darkhold.team.service.TeamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Service
public class GameService {
    private static final String SESSION_QUESTION_NO = "shownQuestionNo";
    private static final String SESSION_QUESTION_SHOWN_AT = "shownQuestionAt";
    private static final int MAX_TIME_FACTOR = 1000;

    private final Logger logger = LogManager.getLogger(GameService.class);

    private final CurrentGame currentGame;

    private final PreviewService previewService;

    private final TeamService teamService;

    private final GameRepository gameRepository;

    public GameService(final CurrentGame currentGame, final PreviewService previewService,
                       final TeamService teamService, final GameRepository gameRepository) {
        this.currentGame = currentGame;
        this.previewService = previewService;
        this.teamService = teamService;
        this.gameRepository = gameRepository;
    }

    public PublishInfo getActiveChallenge() {
        return previewService.getActiveChallenge();
    }

    //todo : this is needed !!!
    public List<String> saveAndGetAllParticipants(final String pin, final String userName) {
        currentGame.saveUserToActiveGame(pin, userName);
        return currentGame.getActiveUsersInGame(pin);
    }

    public List<String> getAllParticipants(final String pin) {
        return currentGame.getActiveUsersInGame(pin);
    }

    public QuestionPointer getCurrentQuestionPointer() {
        String pin = currentPin();
        logger.info("Current pin is {}", pin);
        return currentGame.getCurrentQuestionPointer(pin);
    }

    /**
     * Get current question pointer for a specific PIN (for WebSocket handlers).
     *
     * @param pin game PIN
     * @return current question pointer
     */
    public QuestionPointer getCurrentQuestionPointer(final String pin) {
        logger.info("Current pin is {}", pin);
        return currentGame.getCurrentQuestionPointer(pin);
    }

    public void incrementQuestionNo() {
        String pin = currentPin();
        currentGame.incrementQuestionCount(pin);
    }

    /**
     * save current score.
     *
     * @param name of user
     * @param status of user
     */
    public void saveCurrentScore(final String name, final Integer status) {
        String pin = currentPin();
        currentGame.saveCurrentScore(pin, name, status);
    }

    public String findModerator() {
        String pin = currentPin();
        return currentGame.findModerator(pin);
    }

    /**
     * Whether the principal is the logged-in host of the session's game. Guests may type any name when
     * joining, so a matching name alone is not enough: the principal must also hold a role.
     *
     * @param principal caller
     * @return true if the caller is the moderator of the session's game
     */
    public boolean isModerator(final Principal principal) {
        return isModerator(currentPin(), principal);
    }

    /**
     * Whether the principal is the logged-in host of the given game.
     *
     * @param pin       game PIN
     * @param principal caller
     * @return true if the caller is the moderator of that game
     */
    public boolean isModerator(final String pin, final Principal principal) {
        String moderator = pin == null ? null : currentGame.findModerator(pin);
        return moderator != null && principal instanceof Authentication auth
                && !auth.getAuthorities().isEmpty() && moderator.equals(auth.getName());
    }

    /**
     * Set the status of the persisted game (not the live session).
     *
     * @param pin    game PIN
     * @param status new status
     */
    public void markGame(final String pin, final com.quiz.darkhold.game.entity.GameStatus status) {
        Game game = gameRepository.findByPin(pin);
        if (game != null) {
            game.setGameStatus(status.name());
            gameRepository.save(game);
        }
    }

    /**
     * Title of the challenge a game was created from.
     *
     * @param pin game PIN
     * @return challenge title, or null if unknown
     */
    public String challengeTitle(final String pin) {
        return gameRepository.findByPin(pin) == null
                ? null : previewService.fetchQuestionsFromPin(pin).getChallengeName();
    }

    // ==================== Answer Tracking Methods ====================

    /**
     * Called when a player's game page loads: snapshots the baseline scores once per question and
     * remembers, per HTTP session, which question this player was shown and when (a reload keeps the
     * original start time).
     *
     * @param questionNo zero-based question number
     */
    public void questionShown(final int questionNo) {
        currentGame.startQuestion(currentPin(), questionNo);
        HttpSession session = httpSession();
        if (session != null && !Integer.valueOf(questionNo).equals(session.getAttribute(SESSION_QUESTION_NO))) {
            session.setAttribute(SESSION_QUESTION_NO, questionNo);
            session.setAttribute(SESSION_QUESTION_SHOWN_AT, System.currentTimeMillis());
        }
    }

    /**
     * The question this HTTP session was last shown, or null if none.
     *
     * @return zero-based question number
     */
    public Integer shownQuestionNo() {
        HttpSession session = httpSession();
        return session != null ? (Integer) session.getAttribute(SESSION_QUESTION_NO) : null;
    }

    /**
     * Server-side time since this HTTP session was shown its question.
     *
     * @return elapsed milliseconds
     */
    public long elapsedMillis() {
        HttpSession session = httpSession();
        Long shownAt = session != null ? (Long) session.getAttribute(SESSION_QUESTION_SHOWN_AT) : null;
        return shownAt != null ? Math.max(0, System.currentTimeMillis() - shownAt) : Long.MAX_VALUE;
    }

    /**
     * Atomically record that the player answered a question.
     *
     * @param user       player
     * @param questionNo zero-based question number
     * @return false if the player already answered that question
     */
    public boolean markAnswered(final String user, final int questionNo) {
        return currentGame.markAnswered(currentPin(), user, questionNo);
    }

    /**
     * Question by number in the current game.
     *
     * @param questionNo zero-based question number
     * @return the question, or null if out of range
     */
    public QuestionSet questionAt(final int questionNo) {
        List<QuestionSet> questions = currentGame.getQuestionsOnAPin(currentPin());
        return questionNo >= 0 && questionNo < questions.size() ? questions.get(questionNo) : null;
    }

    public Map<String, Integer> getCurrentScore() {
        String pin = currentPin();
        logger.info("current pin is {}", pin);
        return currentGame.getCurrentScore(pin);
    }

    /**
     * Get current game PIN from HTTP session.
     * This enables support for multiple concurrent games.
     *
     * @return game PIN from session
     * @throws IllegalStateException if there is no game in the session; never guess another game
     */
    private String currentPin() {
        String sessionPin = getSessionPin();
        if (sessionPin == null) {
            throw new IllegalStateException("No game PIN in session");
        }
        return sessionPin;
    }

    /**
     * Get game PIN from current HTTP session.
     *
     * @return PIN from session, or null if not in HTTP context
     */
    private String getSessionPin() {
        HttpSession session = httpSession();
        return session != null ? (String) session.getAttribute("gamePin") : null;
    }

    private HttpSession httpSession() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getSession(false);
    }

    public void cleanUpCurrentGame() {
        String pin = currentPin();
        logger.info("Cleaning up game: {}", pin);
        // make it inactive
        // save scores to a db
        currentGame.stopTheGame(pin);
    }

    // ==================== Streak Methods ====================

    /**
     * Update user streak and return the new streak value.
     *
     * @param name      username
     * @param isCorrect whether the answer was correct
     * @return new streak value
     */
    public int updateStreak(final String name, final boolean isCorrect) {
        String pin = currentPin();
        return currentGame.updateStreak(pin, name, isCorrect);
    }

    /**
     * Get current streak for a user.
     *
     * @param name username
     * @return streak count
     */
    public int getStreak(final String name) {
        String pin = currentPin();
        return currentGame.getStreak(pin, name);
    }

    /**
     * Get previous scores for delta calculation.
     *
     * @return previous scores map
     */
    public Map<String, Integer> getPreviousScores() {
        String pin = currentPin();
        return currentGame.getPreviousScores(pin);
    }

    // ==================== Game Control Methods ====================

    /**
     * Set game status.
     *
     * @param pin    game PIN
     * @param status new status
     */
    public void setGameStatus(final String pin, final GameStatus status) {
        currentGame.setGameStatus(pin, status);
    }

    /**
     * Get current game status.
     *
     * @return game status
     */
    public GameStatus getGameStatus() {
        String pin = currentPin();
        return currentGame.getGameStatus(pin);
    }

    /**
     * Pause a game.
     *
     * @param pin game PIN
     * @return true if paused
     */
    public boolean pauseGame(final String pin) {
        return currentGame.pauseGame(pin);
    }

    /**
     * Resume a game.
     *
     * @param pin game PIN
     * @return elapsed pause time in milliseconds
     */
    public long resumeGame(final String pin) {
        return currentGame.resumeGame(pin);
    }

    /**
     * Skip to the next question.
     *
     * @param pin game PIN
     */
    public void skipQuestion(final String pin) {
        currentGame.incrementQuestionCount(pin);
    }

    // ==================== Player Management Methods ====================

    /**
     * Remove a player from the game (kick).
     *
     * @param pin      game PIN
     * @param username player to remove
     * @return true if removed
     */
    public boolean kickPlayer(final String pin, final String username) {
        return currentGame.removeUserFromGame(pin, username);
    }

    /**
     * Get participant count (excluding moderator).
     *
     * @param pin game PIN
     * @return participant count
     */
    public int getParticipantCount(final String pin) {
        return currentGame.getParticipantCount(pin);
    }

    /**
     * Calculate score with streak multiplier.
     *
     * @param basePoints   base points for correct answer
     * @param timeFactor   time-based factor (0-1000)
     * @param streak       current streak count
     * @return final score with multiplier applied
     */
    public int calculateScoreWithStreak(final int basePoints, final int timeFactor, final int streak) {
        int multiplier = ScoreResult.calculateStreakMultiplier(streak);
        // long: basePoints * timeFactor * multiplier overflows int for large point values
        int finalScore = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, (long) basePoints * timeFactor * multiplier / MAX_TIME_FACTOR));
        logger.info("Score: base={}, time={}, streak={}, mult={}, final={}",
                basePoints, timeFactor, streak, multiplier, finalScore);
        return finalScore;
    }

    /**
     * Get the game mode for the current game.
     *
     * @return GameMode (MULTIPLAYER or PRACTICE)
     */
    public GameMode getGameMode() {
        String pin = currentPin();
        return currentGame.getGameMode(pin);
    }

    /**
     * Get the game mode for a specific PIN (for use when PIN is explicitly available).
     *
     * @param pin game PIN
     * @return GameMode (MULTIPLAYER or PRACTICE)
     */
    public GameMode getGameMode(final String pin) {
        return currentGame.getGameMode(pin);
    }

    /**
     * Check if current game is in practice mode.
     *
     * @return true if practice mode, false otherwise
     */
    public boolean isPracticeMode() {
        return getGameMode() == GameMode.PRACTICE;
    }

    // ==================== Team Mode Methods ====================

    /**
     * Check if current game is in team mode.
     *
     * @return true if team mode, false otherwise
     */
    public boolean isTeamMode() {
        String pin = currentPin();
        return teamService.isTeamMode(pin);
    }

    /**
     * Check if a specific game is in team mode.
     *
     * @param pin game PIN
     * @return true if team mode, false otherwise
     */
    public boolean isTeamMode(final String pin) {
        return teamService.isTeamMode(pin);
    }

    /**
     * Get team scores for current game.
     *
     * @return map of team name to total score
     */
    public Map<String, Integer> getTeamScores() {
        String pin = currentPin();
        return teamService.calculateTeamScores(pin);
    }

    /**
     * Get team scores for a specific game.
     *
     * @param pin game PIN
     * @return map of team name to total score
     */
    public Map<String, Integer> getTeamScores(final String pin) {
        return teamService.calculateTeamScores(pin);
    }
}
