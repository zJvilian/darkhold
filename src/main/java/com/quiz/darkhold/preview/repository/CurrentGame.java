package com.quiz.darkhold.preview.repository;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.game.model.GameMode;
import com.quiz.darkhold.game.model.GameStatus;
import com.quiz.darkhold.game.model.QuestionPointer;
import com.quiz.darkhold.preview.entity.CurrentGameSession;
import com.quiz.darkhold.preview.model.PublishInfo;
import com.quiz.darkhold.team.model.TeamInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CurrentGame {

    private final Logger logger = LogManager.getLogger(CurrentGame.class);

    @Autowired
    private CurrentGameSessionRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In memory per PIN, dropped in stopTheGame: answer statistics and time the current question spent paused
    private final Map<String, GameStats> stats = new ConcurrentHashMap<>();
    private final Map<String, Long> pausedMillis = new ConcurrentHashMap<>();

    /** Running answer tallies for one player or one question. */
    private static final class Tally {
        private int correct;
        private int incorrect;
        private int maxStreak;
        private long totalMillis;
        private int timed;
        private long fastestMillis = -1;

        private synchronized void add(final Boolean right, final int streak, final long millis) {
            if (right != null && right) {
                correct++;
            } else if (right != null) {
                incorrect++;
            }
            maxStreak = Math.max(maxStreak, streak);
            totalMillis += millis;
            timed++;
            fastestMillis = fastestMillis < 0 ? millis : Math.min(fastestMillis, millis);
        }

        private int averageSeconds() {
            return timed == 0 ? 0 : (int) Math.round(totalMillis / 1000.0 / timed);
        }
    }

    private record GameStats(Map<String, Tally> players, Map<Integer, Tally> questions) {
        GameStats() {
            this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        }
    }

    /**
     * save the game info to H2 database before we start the game.
     *
     * @param publishInfo publish info
     */
    public void saveCurrentStatus(final PublishInfo publishInfo, final Deque<QuestionSet> questionSets) {
        List<String> users = new ArrayList<>();
        users.add(publishInfo.getModerator());

        CurrentGameSession session = new CurrentGameSession(publishInfo.getPin(), publishInfo.getModerator());
        session.setUsersList(users);
        session.setQuestionsList(new ArrayList<>(questionSets));
        session.setCurrentQuestionNo(0);
        session.setScoresMap(new HashMap<>());

        repository.save(session);
    }

    /**
     * get the active users in the game.
     *
     * @param pin of the game
     * @return users
     */
    public List<String> getActiveUsersInGame(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        if (session.isPresent()) {
            List<String> users = session.get().getUsersList();
            logger.info("Participants are : {}", users);
            return users;
        }
        return new ArrayList<>();
    }

    /**
     * save user to active game.
     *
     * @param pin      of the game
     * @param userName of user
     */
    @Transactional
    public void saveUserToActiveGame(final String pin, final String userName) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            List<String> users = gameSession.getUsersList();
            if (users == null) {
                users = new ArrayList<>();
            }
            if (users.contains(userName)) {
                // a reconnect or reload joins again; the player is already in
                return;
            }
            users.add(userName);
            gameSession.setUsersList(users);
            repository.save(gameSession);
        }
    }

    /**
     * save questions to active game.
     *
     * @param pin          of the game
     * @param questionSets of the game
     */
    @Transactional
    public void saveQuestionsToActiveGame(final String pin, final List<QuestionSet> questionSets) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            List<QuestionSet> questions = gameSession.getQuestionsList();
            if (questions == null) {
                questions = new ArrayList<>();
            }
            questions.addAll(questionSets);
            gameSession.setQuestionsList(questions);
            repository.save(gameSession);
        }
    }

    /**
     * current question no.
     *
     * @param pin of game
     * @return question no
     */
    public int getCurrentQuestionNo(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        if (session.isPresent()) {
            Integer questionNo = session.get().getCurrentQuestionNo();
            logger.info("getCurrentQuestionNo : questionNo : {}}", questionNo);
            return questionNo != null ? questionNo : 0;
        }
        return 0;
    }

    /**
     * get all questions of the pin.
     *
     * @param pin of game
     * @return question list
     */
    public List<QuestionSet> getQuestionsOnAPin(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        if (session.isPresent()) {
            List<QuestionSet> questions = session.get().getQuestionsList();
            if (questions != null) {
                logger.info("question count : {}", questions.size());
                return questions;
            }
        }
        return new ArrayList<>();
    }

    /**
     * points to the next question.
     *
     * @param pin of game
     */
    @Transactional
    public void incrementQuestionCount(final String pin) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            Integer questionNo = gameSession.getCurrentQuestionNo();
            if (questionNo != null) {
                gameSession.setCurrentQuestionNo(questionNo + 1);
                repository.save(gameSession);
            }
        }
    }

    /**
     * find the moderator.
     *
     * @param pin pin
     * @return moderator
     */
    public String findModerator(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        return session.map(CurrentGameSession::getModerator).orElse(null);
    }

    /**
     * save current score to H2 database.
     *
     * @param pin    pin of game
     * @param name   of user
     * @param status success or not
     */
    @Transactional
    public void saveCurrentScore(final String pin, final String name, final Integer status) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            Map<String, Integer> scores = gameSession.getScoresMap();
            if (scores == null) {
                scores = new HashMap<>();
            }
            if (scores.containsKey(name)) {
                scores.compute(name, (k, currentValue) -> currentValue + status);
            } else {
                scores.put(name, status);
            }
            gameSession.setScoresMap(scores);
            repository.save(gameSession);
        }
    }

    public Map<String, Integer> getCurrentScore(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        if (session.isPresent()) {
            Map<String, Integer> scores = session.get().getScoresMap();
            return scores != null ? scores : new HashMap<>();
        }
        return new HashMap<>();
    }

    public QuestionPointer getCurrentQuestionPointer(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        QuestionPointer questionPointer = new QuestionPointer();

        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            Integer questionNo = gameSession.getCurrentQuestionNo();
            List<QuestionSet> questions = gameSession.getQuestionsList();

            if (questionNo != null) {
                logger.info("questionNo : {}", questionNo);
                if (questions != null) {
                    logger.info("questions size : {}", questions.size());
                    questionPointer.setCurrentQuestionNumber(questionNo);
                    questionPointer.setTotalQuestionCount(questions.size());
                    if (questionNo < questions.size()) {
                        questionPointer.setCurrentQuestion(questions.get(questionNo));
                    }
                }
            }
        }
        return questionPointer;
    }

    public void stopTheGame(final String pin) {
        stats.remove(pin);
        pausedMillis.remove(pin);
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        session.ifPresent(currentGameSession -> repository.delete(currentGameSession));
    }

    // ==================== Streak Methods ====================

    /**
     * Update streak for a user. Increment if correct, reset if incorrect.
     *
     * @param pin       game pin
     * @param username  user name
     * @param isCorrect whether the answer was correct
     * @return the new streak value
     */
    @Transactional
    public int updateStreak(final String pin, final String username, final boolean isCorrect) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            Map<String, Integer> streaks = gameSession.getStreakMap();
            int currentStreak = streaks.getOrDefault(username, 0);
            int newStreak = isCorrect ? currentStreak + 1 : 0;
            streaks.put(username, newStreak);
            gameSession.setStreakMap(streaks);
            repository.save(gameSession);
            logger.info("Updated streak for {}: {} -> {}", username, currentStreak, newStreak);
            return newStreak;
        }
        return 0;
    }

    /**
     * Get current streak for a user.
     *
     * @param pin      game pin
     * @param username user name
     * @return current streak count
     */
    public int getStreak(final String pin, final String username) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        if (session.isPresent()) {
            Map<String, Integer> streaks = session.get().getStreakMap();
            return streaks.getOrDefault(username, 0);
        }
        return 0;
    }

    /**
     * Save current scores as previous scores (for delta calculation).
     * Call this before starting a new question.
     *
     * @param pin game pin
     */
    @Transactional
    public void savePreviousScores(final String pin) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            Map<String, Integer> currentScores = gameSession.getScoresMap();
            if (currentScores != null) {
                gameSession.setPreviousScoresMap(new HashMap<>(currentScores));
                repository.save(gameSession);
            }
        }
    }

    /**
     * Get previous round scores for delta display.
     *
     * @param pin game pin
     * @return previous scores map
     */
    public Map<String, Integer> getPreviousScores(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        if (session.isPresent()) {
            return session.get().getPreviousScoresMap();
        }
        return new HashMap<>();
    }

    // ==================== Answer Tracking Methods ====================

    /**
     * Record that a player answered a question. Atomic: only the first call per player and question wins.
     *
     * @param pin        game pin
     * @param username   player
     * @param questionNo zero-based question number
     * @return true if this was the first answer from the player for that question
     */
    @Transactional
    public boolean markAnswered(final String pin, final String username, final int questionNo) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isEmpty()) {
            return false;
        }
        CurrentGameSession gameSession = session.get();
        Map<String, Integer> answered = gameSession.getAnsweredMap();
        Integer last = answered.get(username);
        if (last != null && last >= questionNo) {
            return false;
        }
        answered.put(username, questionNo);
        gameSession.setAnsweredMap(answered);
        repository.save(gameSession);
        return true;
    }

    /**
     * Whether a player already answered (or timed out on) a question.
     *
     * @param pin        game pin
     * @param username   player
     * @param questionNo zero-based question number
     * @return true once the player's answer for that question is in
     */
    public boolean hasAnswered(final String pin, final String username, final int questionNo) {
        Integer last = repository.findByPin(pin).map(session -> session.getAnsweredMap().get(username)).orElse(null);
        return last != null && last >= questionNo;
    }

    /**
     * Mark a question as started. The first caller per question snapshots the scores as the
     * baseline for the scoreboard delta; later callers (other players loading the page) are no-ops.
     *
     * @param pin        game pin
     * @param questionNo zero-based question number
     */
    @Transactional
    public void startQuestion(final String pin, final int questionNo) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent() && !Integer.valueOf(questionNo).equals(session.get().getStartedQuestionNo())) {
            CurrentGameSession gameSession = session.get();
            gameSession.setStartedQuestionNo(questionNo);
            pausedMillis.remove(pin);
            Map<String, Integer> scores = gameSession.getScoresMap();
            gameSession.setPreviousScoresMap(scores != null ? new HashMap<>(scores) : new HashMap<>());
            repository.save(gameSession);
        }
    }

    // ==================== Game Status Methods ====================

    /**
     * Set the game status.
     *
     * @param pin    game pin
     * @param status new status
     */
    @Transactional
    public void setGameStatus(final String pin, final GameStatus status) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            gameSession.setGameStatus(status);
            repository.save(gameSession);
            logger.info("Game {} status set to {}", pin, status);
        }
    }

    /**
     * Get the current game status.
     *
     * @param pin game pin
     * @return game status
     */
    public GameStatus getGameStatus(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        if (session.isPresent()) {
            GameStatus status = session.get().getGameStatus();
            return status != null ? status : GameStatus.WAITING;
        }
        return GameStatus.WAITING;
    }

    /**
     * Pause the game.
     *
     * @param pin game pin
     * @return true if paused successfully
     */
    @Transactional
    public boolean pauseGame(final String pin) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            gameSession.setGameStatus(GameStatus.PAUSED);
            gameSession.setPausedAt(System.currentTimeMillis());
            repository.save(gameSession);
            logger.info("Game {} paused", pin);
            return true;
        }
        return false;
    }

    /**
     * Resume a paused game.
     *
     * @param pin game pin
     * @return elapsed pause time in milliseconds (for timer adjustment)
     */
    @Transactional
    public long resumeGame(final String pin) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            gameSession.setGameStatus(GameStatus.ACTIVE);
            long elapsed = calculateElapsedPause(gameSession.getPausedAt());
            pausedMillis.merge(pin, elapsed, Long::sum);
            gameSession.setPausedAt(null);
            repository.save(gameSession);
            logger.info("Game {} resumed after {}ms pause", pin, elapsed);
            return elapsed;
        }
        return 0;
    }

    private long calculateElapsedPause(final Long pausedAt) {
        return pausedAt != null ? System.currentTimeMillis() - pausedAt : 0;
    }

    /**
     * How long the current question has been paused, including a pause still going on.
     *
     * @param pin game pin
     * @return paused milliseconds
     */
    public long getPausedMillis(final String pin) {
        Long running = repository.findByPin(pin).map(CurrentGameSession::getPausedAt).orElse(null);
        return pausedMillis.getOrDefault(pin, 0L) + calculateElapsedPause(running);
    }

    // ==================== Answer Statistics ====================

    /**
     * Count an answer towards the player's and the question's statistics. Timeouts are not answers: skip them.
     *
     * @param pin        game pin
     * @param username   player
     * @param questionNo zero-based question number
     * @param scored     whether the question has a right answer (not a poll or word cloud)
     * @param correct    whether the answer was accepted
     * @param streak     the player's streak after this answer
     * @param millis     time taken to answer
     */
    public void recordAnswer(final String pin, final String username, final int questionNo, final boolean scored,
                             final boolean correct, final int streak, final long millis) {
        GameStats game = stats.computeIfAbsent(pin, key -> new GameStats());
        long time = Math.max(0, millis);
        game.players().computeIfAbsent(username, key -> new Tally()).add(scored ? correct : null, streak, time);
        game.questions().computeIfAbsent(questionNo, key -> new Tally()).add(correct, 0, time);
    }

    private Optional<Tally> player(final String pin, final String username) {
        return Optional.ofNullable(stats.get(pin)).map(game -> game.players().get(username));
    }

    private Optional<Tally> question(final String pin, final int questionNo) {
        return Optional.ofNullable(stats.get(pin)).map(game -> game.questions().get(questionNo));
    }

    public int getPlayerCorrectCount(final String pin, final String username) {
        return player(pin, username).map(tally -> tally.correct).orElse(0);
    }

    public int getPlayerIncorrectCount(final String pin, final String username) {
        return player(pin, username).map(tally -> tally.incorrect).orElse(0);
    }

    public int getPlayerMaxStreak(final String pin, final String username) {
        return player(pin, username).map(tally -> tally.maxStreak).orElse(0);
    }

    public int getPlayerAvgAnswerTimeSeconds(final String pin, final String username) {
        return player(pin, username).map(Tally::averageSeconds).orElse(0);
    }

    public int getQuestionCorrectCount(final String pin, final int questionNo) {
        return question(pin, questionNo).map(tally -> tally.correct).orElse(0);
    }

    public int getQuestionIncorrectCount(final String pin, final int questionNo) {
        return question(pin, questionNo).map(tally -> tally.incorrect).orElse(0);
    }

    public int getQuestionAvgAnswerTimeSeconds(final String pin, final int questionNo) {
        return question(pin, questionNo).map(Tally::averageSeconds).orElse(0);
    }

    public int getQuestionFastestAnswerTimeSeconds(final String pin, final int questionNo) {
        return question(pin, questionNo).filter(tally -> tally.fastestMillis >= 0)
                .map(tally -> (int) Math.round(tally.fastestMillis / 1000.0)).orElse(0);
    }

    // ==================== Player Management Methods ====================

    /**
     * Remove a user from the game (kick functionality).
     *
     * @param pin      game pin
     * @param username user to remove
     * @return true if removed successfully
     */
    @Transactional
    public boolean removeUserFromGame(final String pin, final String username) {
        Optional<CurrentGameSession> session = repository.lockByPin(pin);
        if (session.isPresent()) {
            CurrentGameSession gameSession = session.get();
            List<String> users = gameSession.getUsersList();
            if (users != null && users.remove(username)) {
                gameSession.setUsersList(users);
                // Also remove from scores and streaks
                removeUserFromMaps(gameSession, username);
                repository.save(gameSession);
                logger.info("User {} removed from game {}", username, pin);
                return true;
            }
        }
        return false;
    }

    private void removeUserFromMaps(final CurrentGameSession session, final String username) {
        Map<String, Integer> scores = session.getScoresMap();
        if (scores != null) {
            scores.remove(username);
            session.setScoresMap(scores);
        }
        Map<String, Integer> streaks = session.getStreakMap();
        if (streaks != null) {
            streaks.remove(username);
            session.setStreakMap(streaks);
        }
        Map<String, String> playerTeams = session.getPlayerTeamsMap();
        if (playerTeams.remove(username) != null) {
            session.setPlayerTeamsMap(playerTeams);
            removeFromTeams(session, username);
        }
    }

    private void removeFromTeams(final CurrentGameSession session, final String username) {
        try {
            List<TeamInfo> teams = objectMapper.readValue(session.getTeamsJson(),
                    new TypeReference<List<TeamInfo>>() { });
            teams.forEach(team -> team.removeMember(username));
            session.setTeamsJson(objectMapper.writeValueAsString(teams));
        } catch (JacksonException | IllegalArgumentException ex) {
            logger.error("Could not remove {} from the teams", username, ex);
        }
    }

    /**
     * Get participant count.
     *
     * @param pin game pin
     * @return number of participants (excluding moderator)
     */
    public int getParticipantCount(final String pin) {
        List<String> users = getActiveUsersInGame(pin);
        String moderator = findModerator(pin);
        if (users == null) {
            return 0;
        }
        // Subtract 1 for moderator if present
        return moderator != null && users.contains(moderator) ? users.size() - 1 : users.size();
    }

    /**
     * Get game mode for a specific game.
     *
     * @param pin game pin
     * @return GameMode (MULTIPLAYER or PRACTICE)
     */
    public GameMode getGameMode(final String pin) {
        Optional<CurrentGameSession> session = repository.findByPin(pin);
        if (session.isPresent()) {
            GameMode mode = session.get().getGameMode();
            return mode != null ? mode : GameMode.MULTIPLAYER;
        }
        return GameMode.MULTIPLAYER;
    }
}
