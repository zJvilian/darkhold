package com.quiz.darkhold.analytics.service;

import com.quiz.darkhold.analytics.entity.GameResult;
import com.quiz.darkhold.analytics.entity.ParticipantResult;
import com.quiz.darkhold.analytics.entity.QuestionResult;
import com.quiz.darkhold.analytics.repository.GameResultRepository;
import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.game.entity.Game;
import com.quiz.darkhold.game.model.GameMode;
import com.quiz.darkhold.game.repository.GameRepository;
import com.quiz.darkhold.preview.repository.CurrentGame;
import com.quiz.darkhold.team.entity.TeamResult;
import com.quiz.darkhold.team.model.TeamInfo;
import com.quiz.darkhold.team.repository.TeamResultRepository;
import com.quiz.darkhold.team.service.TeamService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for saving and retrieving game analytics and results.
 */
@Service
public class ResultService {

    private static final Logger logger = LogManager.getLogger(ResultService.class);
    private static final int MODERATOR_RANK_OFFSET = 1;

    private final GameResultRepository gameResultRepository;
    private final GameRepository gameRepository;
    private final CurrentGame currentGame;
    private final TeamService teamService;
    private final TeamResultRepository teamResultRepository;

    public ResultService(final GameResultRepository gameResultRepository,
                         final GameRepository gameRepository,
                         final CurrentGame currentGame,
                         final TeamService teamService,
                         final TeamResultRepository teamResultRepository) {
        this.gameResultRepository = gameResultRepository;
        this.gameRepository = gameRepository;
        this.currentGame = currentGame;
        this.teamService = teamService;
        this.teamResultRepository = teamResultRepository;
    }

    /**
     * Save complete game results when a game ends. Idempotent per PIN: a repeat returns the saved result.
     *
     * @param pin           game PIN
     * @param challengeName challenge name
     * @return saved GameResult entity
     */
    @Transactional
    public GameResult saveGameResult(final String pin, final String challengeName) {
        logger.info("Saving game result for PIN: {}", pin);

        Optional<GameResult> existing = gameResultRepository.findFirstByPinOrderByIdAsc(pin);
        if (existing.isPresent()) {
            logger.info("Game result already saved for PIN: {}", pin);
            return existing.get();
        }

        Game game = gameRepository.findByPin(pin);
        if (game == null) {
            logger.error("Game not found for PIN: {}", pin);
            return null;
        }

        GameResult gameResult = createGameResult(pin, challengeName, game);
        boolean teamMode = Boolean.TRUE.equals(game.getTeamMode());
        addParticipantResults(gameResult, pin, teamMode, isPractice(game.getGameMode()));
        addQuestionResults(gameResult, pin);

        GameResult saved = gameResultRepository.save(gameResult);
        if (teamMode) {
            // team rows point at the game result, so it has to be stored first
            addTeamResults(gameResult, pin);
        }
        logger.info("Game result saved with ID: {}", saved.getId());
        return saved;
    }

    private static boolean isPractice(final String gameMode) {
        return GameMode.PRACTICE.name().equals(gameMode);
    }

    /**
     * Registered accounts are keyed as "Name#id"; people only see the name.
     *
     * @param username the stored player key
     * @return the name without the account id
     */
    public static String displayName(final String username) {
        return username == null ? null : username.replaceFirst("#\\d+$", "");
    }

    @SuppressWarnings("PMD.ExcessiveMethodLength")
    private GameResult createGameResult(final String pin, final String challengeName, final Game game) {
        GameResult gameResult = new GameResult();
        gameResult.setPin(pin);
        gameResult.setChallengeId(game.getChallengeId());
        gameResult.setChallengeName(challengeName);
        gameResult.setModerator(game.getModerator() != null ? game.getModerator() : "Unknown");
        gameResult.setGameMode(game.getGameMode());
        gameResult.setTeamMode(game.getTeamMode());

        List<QuestionSet> questions = currentGame.getQuestionsOnAPin(pin);
        gameResult.setTotalQuestions(questions.size());

        List<String> participants = currentGame.getActiveUsersInGame(pin);
        if (isPractice(game.getGameMode())) {
            gameResult.setParticipantCount(1); // the host is the only player
        } else {
            gameResult.setParticipantCount(participants != null
                    ? Math.max(0, participants.size() - MODERATOR_RANK_OFFSET) : 0);
        }

        gameResult.setStartedAt(game.getCreatedOn());
        gameResult.setCompletedAt(LocalDateTime.now(ZoneId.systemDefault()));

        Duration duration = Duration.between(game.getCreatedOn(), gameResult.getCompletedAt());
        gameResult.setDurationMinutes((int) duration.toMinutes());

        return gameResult;
    }

    @SuppressWarnings("PMD.ExcessiveMethodLength")
    private void addParticipantResults(final GameResult gameResult, final String pin,
                                       final boolean isTeamMode, final boolean practice) {
        Map<String, Integer> scores = currentGame.getCurrentScore(pin);
        if (scores == null || scores.isEmpty()) {
            logger.warn("No scores found for game: {}", pin);
            return;
        }

        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        String moderator = currentGame.findModerator(pin);
        int position = 0;
        int rank = 0;
        Integer previousScore = null;

        List<TeamInfo> teams = null;
        if (isTeamMode) {
            teams = teamService.getTeams(pin);
        }

        for (Map.Entry<String, Integer> entry : sortedScores) {
            String username = entry.getKey();
            if (!practice && username.equals(moderator)) {
                continue; // the moderator does not play in a live game, but is the player in practice
            }

            ParticipantResult participantResult = new ParticipantResult();
            participantResult.setGameResult(gameResult);
            participantResult.setUsername(displayName(username));
            participantResult.setFinalScore(entry.getValue());
            position++;
            // competition ranking: equal scores share a rank, the next rank skips ahead
            if (!entry.getValue().equals(previousScore)) {
                rank = position;
            }
            previousScore = entry.getValue();
            participantResult.setFinalRank(rank);

            int maxStreak = Math.max(
                    currentGame.getPlayerMaxStreak(pin, username),
                    currentGame.getStreak(pin, username));
            participantResult.setMaxStreak(maxStreak);

            if (isTeamMode && teams != null) {
                String teamName = teamService.getPlayerTeam(pin, username);
                if (teamName != null) {
                    participantResult.setTeamName(teamName);
                    String teamColor = teams.stream()
                            .filter(t -> t.getName().equals(teamName))
                            .map(TeamInfo::getColor)
                            .findFirst()
                            .orElse(null);
                    participantResult.setTeamColor(teamColor);
                }
            }

            participantResult.setCorrectAnswers(currentGame.getPlayerCorrectCount(pin, username));
            participantResult.setIncorrectAnswers(currentGame.getPlayerIncorrectCount(pin, username));
            participantResult.setAverageAnswerTimeSeconds(currentGame.getPlayerAvgAnswerTimeSeconds(pin, username));

            gameResult.getParticipantResults().add(participantResult);

            if (position == 1) {
                gameResult.setWinnerUsername(displayName(username));
                gameResult.setWinnerScore(entry.getValue());
            }
        }
    }

    @SuppressWarnings("PMD.ExcessiveMethodLength")
    private void addQuestionResults(final GameResult gameResult, final String pin) {
        List<QuestionSet> questions = currentGame.getQuestionsOnAPin(pin);
        if (questions == null || questions.isEmpty()) {
            return;
        }

        int participants = gameResult.getParticipantCount() != null ? gameResult.getParticipantCount() : 0;
        for (int index = 0; index < questions.size(); index++) {
            QuestionSet question = questions.get(index);
            QuestionResult questionResult = new QuestionResult();
            questionResult.setGameResult(gameResult);
            questionResult.setQuestionNumber(index + 1);
            questionResult.setQuestionText(question.getQuestion());
            questionResult.setQuestionType(question.getQuestionType() != null
                    ? question.getQuestionType().name() : "MULTIPLE_CHOICE");

            int correct = currentGame.getQuestionCorrectCount(pin, index);
            int incorrect = currentGame.getQuestionIncorrectCount(pin, index);
            questionResult.setCorrectCount(correct);
            questionResult.setIncorrectCount(incorrect);
            questionResult.setTimeoutCount(Math.max(0, participants - correct - incorrect));
            questionResult.setAverageAnswerTimeSeconds(currentGame.getQuestionAvgAnswerTimeSeconds(pin, index));
            questionResult.setFastestAnswerTimeSeconds(currentGame.getQuestionFastestAnswerTimeSeconds(pin, index));

            gameResult.getQuestionResults().add(questionResult);
        }
    }

    /**
     * Get every saved game result, newest first (for admins).
     *
     * @return all game results
     */
    public List<GameResult> getAllGameResults() {
        return gameResultRepository.findAll(Sort.by(Sort.Direction.DESC, "completedAt"));
    }

    /**
     * Delete a saved game result with its participant, question and team rows.
     *
     * @param id game result ID
     */
    @Transactional
    public void deleteGameResult(final Long id) {
        teamResultRepository.deleteAll(teamResultRepository.findByGameResultId(id));
        gameResultRepository.deleteById(id);
    }

    /**
     * Get the saved team standings of a game, best team first.
     *
     * @param id game result ID
     * @return team results, empty when the game had no teams
     */
    public List<TeamResult> getTeamResults(final Long id) {
        return teamResultRepository.findByGameResultIdOrderByFinalRankAsc(id);
    }

    /**
     * Get all game results for a specific moderator.
     *
     * @param moderator moderator username
     * @return list of game results
     */
    public List<GameResult> getGameResultsByModerator(final String moderator) {
        return gameResultRepository.findByModeratorOrderByCompletedAtDesc(moderator);
    }

    /**
     * Get detailed game result by ID.
     *
     * @param id game result ID
     * @return game result with all details
     */
    @Transactional(readOnly = true)
    public GameResult getGameResultById(final Long id) {
        GameResult result = gameResultRepository.findById(id).orElse(null);
        if (result != null) {
            // the result page and the CSV export read both lists after this transaction has ended
            org.hibernate.Hibernate.initialize(result.getParticipantResults());
            org.hibernate.Hibernate.initialize(result.getQuestionResults());
        }
        return result;
    }

    /**
     * Get all game results for a challenge.
     *
     * @param challengeId challenge ID
     * @return list of game results
     */
    public List<GameResult> getGameResultsByChallenge(final String challengeId) {
        return gameResultRepository.findByChallengeIdOrderByCompletedAtDesc(challengeId);
    }

    /**
     * Add team results to game result.
     *
     * @param gameResult game result entity
     * @param pin        game PIN
     */
    private void addTeamResults(final GameResult gameResult, final String pin) {
        List<TeamInfo> teams = new ArrayList<>(teamService.getTeams(pin));
        Map<String, Integer> teamScores = teamService.calculateTeamScores(pin);
        // a team that scored nothing is still a team in the standings
        teams.sort(Comparator.comparing((TeamInfo t) -> teamScores.getOrDefault(t.getName(), 0)).reversed());

        int position = 0;
        int rank = 0;
        Integer previousScore = null;
        for (TeamInfo teamInfo : teams) {
            String teamName = teamInfo.getName();
            Integer teamScore = teamScores.getOrDefault(teamName, 0);

            position++;
            if (!teamScore.equals(previousScore)) {
                rank = position;
            }
            previousScore = teamScore;

            TeamResult teamResult = new TeamResult();
            teamResult.setGameResult(gameResult);
            teamResult.setTeamName(teamName);
            teamResult.setTeamColor(teamInfo.getColor());
            teamResult.setFinalScore(teamScore);
            teamResult.setFinalRank(rank);
            teamResult.setMemberCount(teamInfo.getMemberCount());
            teamResult.setAverageScorePerMember(
                    teamInfo.getMemberCount() > 0 ? teamScore / teamInfo.getMemberCount() : 0);

            teamResultRepository.save(teamResult);

            if (position == 1) {
                gameResult.setWinningTeamName(teamName);
                gameResult.setWinningTeamScore(teamScore);
            }
        }
    }
}
