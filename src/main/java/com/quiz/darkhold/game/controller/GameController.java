package com.quiz.darkhold.game.controller;

import com.quiz.darkhold.challenge.entity.QuestionSet;
import com.quiz.darkhold.challenge.entity.QuestionType;
import com.quiz.darkhold.game.model.Challenge;
import com.quiz.darkhold.game.model.CurrentScore;
import com.quiz.darkhold.game.model.Game;
import com.quiz.darkhold.game.model.GameMode;
import com.quiz.darkhold.game.model.GameStatus;
import com.quiz.darkhold.game.model.QuestionPointer;
import com.quiz.darkhold.game.model.StartTrigger;
import com.quiz.darkhold.game.model.UserResponse;
import com.quiz.darkhold.analytics.service.ResultService;
import com.quiz.darkhold.game.service.AnswerValidationService;
import com.quiz.darkhold.game.service.GameService;
import com.quiz.darkhold.init.GameConfig;
import com.quiz.darkhold.game.model.ScoreResult;
import com.quiz.darkhold.team.model.TeamAssignmentMethod;
import com.quiz.darkhold.team.model.TeamInfo;
import com.quiz.darkhold.team.service.TeamService;
import com.quiz.darkhold.util.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
@Controller
public class GameController {
    private final Logger logger = LogManager.getLogger(GameController.class);

    private static final int MAX_TIME_FACTOR = 1000;
    private static final int MESSAGE_PARTS_KICK_PLAYER = 2;
    private static final int MESSAGE_PARTS_TEAM_ASSIGN = 3;

    private final GameService gameService;
    private final GameConfig gameConfig;
    private final AnswerValidationService answerValidationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ResultService resultService;
    private final TeamService teamService;

    public GameController(final GameService gameService,
                          final GameConfig gameConfig,
                          final AnswerValidationService answerValidationService,
                          final SimpMessagingTemplate messagingTemplate,
                          final ResultService resultService,
                          final TeamService teamService) {
        this.gameService = gameService;
        this.gameConfig = gameConfig;
        this.answerValidationService = answerValidationService;
        this.messagingTemplate = messagingTemplate;
        this.resultService = resultService;
        this.teamService = teamService;
    }

    @PostMapping("/interstitial")
    public String startInterstitial(final Model model, @RequestParam("quiz_pin") final String quizPin) {
        String sanitizedPin = CommonUtils.sanitizedString(quizPin);
        logger.info("On to interstitial : {}", sanitizedPin);
        model.addAttribute("quizPin", quizPin);
        return "interstitial";
    }

    /**
     * On to the page where the only question is getting displayed for few seconds.
     *
     * @param model     model
     * @param quizPin   the quiz PIN
     * @param principal auth
     * @return question page
     */
    @PostMapping("/question")
    public String question(final Model model,
                           @RequestParam(value = "quizPin", required = false) final String quizPin,
                           final Principal principal) {
        logger.info("On to question : {}", quizPin);
        QuestionPointer questionPointer = quizPin != null
                ? gameService.getCurrentQuestionPointer(quizPin)
                : gameService.getCurrentQuestionPointer();
        logger.info("Question pointer: current={}, total={}",
                questionPointer.getCurrentQuestionNumber(),
                questionPointer.getTotalQuestionCount());

        // Check if question pointer is valid (total count should be > 0)
        if (questionPointer.getTotalQuestionCount() == 0) {
            logger.error("Invalid question pointer - total count is 0. PIN: {}", quizPin);
            return finalScore(model, quizPin, principal);
        }

        if (questionPointer.getCurrentQuestionNumber() == questionPointer.getTotalQuestionCount()) {
            logger.warn("Game ending: currentQuestion == totalQuestions ({} == {})",
                    questionPointer.getCurrentQuestionNumber(),
                    questionPointer.getTotalQuestionCount());
            return finalScore(model, quizPin, principal);
        }
        model.addAttribute("quizPin", quizPin);
        logger.info("Going to question page");
        return "question";
    }

    /**
     * Final scoreboard. Every client views it; only the moderator finalizes the game (saves the
     * result once and marks the game finished). The session itself stays until the scheduled cleanup
     * so players who load this page after the moderator still see the scores.
     *
     * @param model     model
     * @param quizPin   the quiz PIN
     * @param principal auth
     * @return final score page
     */
    @PostMapping("/final")
    public String finalScore(final Model model,
                             @RequestParam(value = "quizPin", required = false) final String quizPin,
                             final Principal principal) {
        logger.info("On to the finalScore for PIN: {}", quizPin);
        CurrentScore score = new CurrentScore();
        Map<String, Integer> scores = gameService.getCurrentScore();
        // Accounts are keyed "<first name>#<id>"; the podium shows the name only
        Map<String, Integer> shown = new java.util.LinkedHashMap<>();
        scores.forEach((name, points) -> shown.merge(name.replaceFirst("#\\d+$", ""), points, Integer::sum));
        score.setScore(shown);
        model.addAttribute("score", score);

        // Check if team mode and add team scores
        if (quizPin != null && gameService.isTeamMode(quizPin)) {
            // The podium reads team name, colour, total and member count, so it needs the ranked results
            List<ScoreResult> players = scores.entrySet().stream()
                    .map(e -> new ScoreResult(e.getKey(), e.getValue(), 0, 0, 0, 0)).toList();
            model.addAttribute("teamScores", teamService.getTeamScoreResults(quizPin, players));
            model.addAttribute("isTeamMode", true);
            List<TeamInfo> teams = teamService.getTeams(quizPin);
            model.addAttribute("teams", teams);
        } else {
            model.addAttribute("isTeamMode", false);
        }

        if (quizPin != null && gameService.isModerator(quizPin, principal)) {
            finalizeGame(quizPin);
        }
        return "finalscore";
    }

    private void finalizeGame(final String quizPin) {
        saveGameResultIfAvailable(quizPin);
        gameService.markGame(quizPin, com.quiz.darkhold.game.entity.GameStatus.FINISHED);
        if (gameService.getGameMode(quizPin) == GameMode.PRACTICE) {
            // nobody else views a practice result
            gameService.cleanUpCurrentGame();
        }
    }

    private void saveGameResultIfAvailable(final String quizPin) {
        try {
            String title = gameService.challengeTitle(quizPin);
            resultService.saveGameResult(quizPin, title != null ? title : "Quiz Session");
            logger.info("Game results saved for PIN: {}", quizPin);
            // OK to catch RuntimeException
        } catch (RuntimeException ex) {
            logger.error("Failed to save game results for PIN: {}", quizPin, ex);
        }
    }

    /**
     * On to the game.
     *
     * @param model     model
     * @param quizPin   the quiz PIN
     * @param principal auth
     * @return game page
     */
    @PostMapping("/game")
    public String startGame(final Model model,
                            @RequestParam(value = "quizPin", required = false) final String quizPin,
                            final Principal principal) {
        logger.info("On to game : {}", quizPin);
        QuestionPointer questionPointer = quizPin != null
                ? gameService.getCurrentQuestionPointer(quizPin)
                : gameService.getCurrentQuestionPointer();
        QuestionSet currentQuestion = questionPointer.getCurrentQuestion();
        if (currentQuestion == null) {
            logger.warn("No current question for PIN {}, going to final score", quizPin);
            return finalScore(model, quizPin, principal);
        }
        gameService.questionShown(questionPointer.getCurrentQuestionNumber());
        Challenge challenge = buildChallengeModel(questionPointer, currentQuestion);
        GameMode gameMode = quizPin != null
                ? gameService.getGameMode(quizPin)
                : gameService.getGameMode();

        model.addAttribute("challenge", challenge);
        model.addAttribute("quizPin", quizPin);
        model.addAttribute("gameMode", gameMode.name());
        addQuestionAttributes(model, currentQuestion);
        return "game";
    }

    private Challenge buildChallengeModel(final QuestionPointer qp, final QuestionSet qs) {
        Challenge challenge = new Challenge();
        challenge.setQuestionNumber(qp.getCurrentQuestionNumber());
        challenge.setTotalQuestions(qp.getTotalQuestionCount());
        challenge.setQuestionSet(qs);
        challenge.setQuestionNumber(challenge.getQuestionNumber() + 1);
        return challenge;
    }

    private void addQuestionAttributes(final Model model, final QuestionSet qs) {
        int timeLimit = qs.getTimeLimit() != null ? qs.getTimeLimit() : gameConfig.getTimerSeconds();
        model.addAttribute("game_timer", String.valueOf(timeLimit));
        QuestionType qType = qs.getQuestionType() != null ? qs.getQuestionType() : QuestionType.MULTIPLE_CHOICE;
        model.addAttribute("question_type", qType.name());
        int points = qs.getPoints() != null ? qs.getPoints() : 1000;
        model.addAttribute("question_points", String.valueOf(points));
    }

    /**
     * Record the caller's answer to the question they were shown. The player, the correctness and the
     * elapsed time are all derived server-side; only the chosen option comes from the client.
     *
     * @param selectedAnswer chosen option (A-D or TRUE/FALSE), empty on timeout
     * @param principal      the answering player
     * @return false if the answer was ignored (no open question, or already answered)
     */
    @PostMapping("/answer/")
    public @ResponseBody
    Boolean enterGame(@RequestParam(value = "selectedAnswer", required = false, defaultValue = "")
                      final String selectedAnswer,
                      final Principal principal) {
        String user = principal.getName();
        Integer questionNo = gameService.shownQuestionNo();
        QuestionSet question = questionNo == null ? null : gameService.questionAt(questionNo);
        if (question == null || !gameService.markAnswered(user, questionNo)) {
            logger.info("Ignoring answer from {}: no open question or already answered",
                    CommonUtils.sanitizedString(user));
            return false;
        }
        boolean isModerator = gameService.isModerator(principal);
        boolean isPracticeMode = gameService.isPracticeMode();

        // In practice mode, moderator CAN score. In multiplayer, moderator cannot.
        if (!isModerator || isPracticeMode) {
            scoreAnswer(user, question, answerValidationService.validateSelection(question, selectedAnswer));
        }
        // Multiplayer: the moderator's answer post moves the game on. Practice: the player is the moderator.
        if (isModerator) {
            gameService.incrementQuestionNo();
        }
        return true;
    }

    private void scoreAnswer(final String user, final QuestionSet question, final boolean isCorrect) {
        int streak = gameService.updateStreak(user, isCorrect);
        int basePoints = answerValidationService.shouldScore(question)
                ? answerValidationService.getPoints(question) : 0;
        int scoreOnStatus = isCorrect
                ? calculateTimeFactor(question, gameService.elapsedMillis(), basePoints, streak) : 0;
        logger.info("score is {} (streak: {}, base: {})", scoreOnStatus, streak, basePoints);
        gameService.saveCurrentScore(user, scoreOnStatus);
    }

    /**
     * Validate a TYPE_ANSWER response server-side using fuzzy matching.
     *
     * @param userAnswer the user's typed answer
     * @param principal  the answering player
     * @return "correct" or "incorrect"
     */
    @PostMapping("/validate_answer/")
    public @ResponseBody
    String validateTypeAnswer(@RequestParam(value = "userAnswer", required = false, defaultValue = "")
                              final String userAnswer,
                              final Principal principal) {
        String user = principal.getName();
        boolean isModerator = gameService.isModerator(principal);
        boolean isPracticeMode = gameService.isPracticeMode();
        Integer questionNo = gameService.shownQuestionNo();
        QuestionSet question = questionNo == null ? null : gameService.questionAt(questionNo);
        // A repeat answers "incorrect" so the endpoint can't be used to probe for the right answer.
        if ((isModerator && !isPracticeMode) || question == null || !gameService.markAnswered(user, questionNo)) {
            logger.info("Ignoring typed answer from {}", CommonUtils.sanitizedString(user));
            return "incorrect";
        }
        boolean isCorrect = answerValidationService.validateAnswer(question, userAnswer);
        scoreAnswer(user, question, isCorrect);
        if (isModerator) {
            // practice mode: scoring player is the moderator and moves the game on
            gameService.incrementQuestionNo();
        }
        return isCorrect ? "correct" : "incorrect";
    }

    private int calculateTimeFactor(final QuestionSet question, final long elapsedMs,
                                    final int basePoints, final int streak) {
        int timeLimit = question.getTimeLimit() != null && question.getTimeLimit() > 0
                ? question.getTimeLimit() : gameConfig.getTimerSeconds();
        long timerMs = timeLimit * 1000L;
        long timeTakenMs = Math.min(Math.max(0, elapsedMs), timerMs);
        int timeFactor = (int) ((timerMs - timeTakenMs) * MAX_TIME_FACTOR / timerMs);
        return gameService.calculateScoreWithStreak(basePoints, timeFactor, streak);
    }

    /**
     * Always active, will add the new user and give it back in response.
     * Sends to PIN-scoped topic for concurrent game support.
     * Auto-assigns player to team if team mode is enabled with BALANCED or RANDOM assignment.
     *
     * @param game      game
     * @param principal the joining player; may only add themselves
     */
    @MessageMapping("/user")
    public void getGame(final Game game, final Principal principal) {
        logger.info("On to getGame : {}", game);
        if (principal == null || game.getName() == null || !game.getName().equals(principal.getName())) {
            logger.warn("Ignoring join for {}: does not match the connected user", game);
            return;
        }
        List<String> users = gameService.saveAndGetAllParticipants(game.getPin(), game.getName());

        // Auto-assign to team if team mode enabled and not MANUAL assignment
        autoAssignToTeamIfEnabled(game.getPin(), game.getName());

        UserResponse response = new UserResponse(users);
        messagingTemplate.convertAndSend("/topic/" + game.getPin() + "/user", response);
    }

    private void autoAssignToTeamIfEnabled(final String pin, final String username) {
        if (!teamService.isTeamMode(pin)) {
            return;
        }

        TeamAssignmentMethod method = teamService.getAssignmentMethod(pin);
        if (method == TeamAssignmentMethod.MANUAL) {
            logger.info("Team mode is MANUAL, skipping auto-assignment for {}", username);
            return;
        }

        // Check if player is already assigned to a team
        String existingTeam = teamService.getPlayerTeam(pin, username);
        if (existingTeam != null) {
            logger.info("Player {} already assigned to team {}", username, existingTeam);
            return;
        }

        logger.info("Auto-assigning player {} to team using {} method", username, method);
        teamService.autoAssignPlayer(pin, username, method);

        // Broadcast team update to all clients
        List<TeamInfo> teams = teamService.getTeams(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/team_update", teams);
    }

    /**
     * Trigger for starting the game.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin       of the game
     * @param principal of who started it
     */
    @MessageMapping("/start")
    public void startTrigger(final String pin, final Principal principal) {
        // this is triggered by the game moderator
        logger.info("On to startGame : {}", pin);
        if (!requireHost(pin, principal)) {
            return;
        }
        logger.info("On to startGame : user : {}", principal.getName());
        gameService.markGame(pin, com.quiz.darkhold.game.entity.GameStatus.STARTED);
        StartTrigger trigger = new StartTrigger(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/start", trigger);
    }

    /**
     * Fetch current question for display.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param message format: "pin:username" or just "pin"
     */
    @MessageMapping("/question_fetch")
    public void questionFetch(final String message) {
        logger.info("On to questionFetch : {}", message);
        // Extract PIN from message (format: "pin" or "pin:username")
        String pin = message.contains(":") ? message.substring(0, message.indexOf(":")) : message;
        QuestionPointer questionPointer = gameService.getCurrentQuestionPointer(pin);
        StartTrigger trigger;
        if (questionPointer.getCurrentQuestion() == null) {
            trigger = new StartTrigger("END_GAME");
        } else {
            logger.info("On questionPointer.getCurrentQuestionNumber() : {}",
                    questionPointer.getCurrentQuestionNumber());
            trigger = new StartTrigger(questionPointer.getCurrentQuestionNumber() + 1 + " : "
                    + questionPointer.getCurrentQuestion().getQuestion());
        }
        messagingTemplate.convertAndSend("/topic/" + pin + "/question_read", trigger);
    }

    /**
     * Signal that scores are ready to be fetched.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin game PIN
     */
    @MessageMapping("/fetch_scores")
    public void scoresFetch(final String pin, final Principal principal) {
        logger.info("On to scoresFetch for game: {}", pin);
        if (!requireHost(pin, principal)) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/" + pin + "/read_scores", true);
    }

    // ==================== Game Control WebSocket Endpoints ====================

    /**
     * Pause the current game.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin game pin
     */
    @MessageMapping("/pause_game")
    public void pauseGame(final String pin, final Principal principal) {
        logger.info("Pausing game: {}", pin);
        if (!requireHost(pin, principal)) {
            return;
        }
        boolean result = gameService.pauseGame(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/game_paused", result);
    }

    /**
     * Resume a paused game.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin game pin
     */
    @MessageMapping("/resume_game")
    public void resumeGame(final String pin, final Principal principal) {
        logger.info("Resuming game: {}", pin);
        if (!requireHost(pin, principal)) {
            return;
        }
        long elapsed = gameService.resumeGame(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/game_resumed", elapsed);
    }

    /**
     * Skip the current question.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin game pin
     */
    @MessageMapping("/skip_question")
    public void skipQuestion(final String pin, final Principal principal) {
        logger.info("Skipping question for game: {}", pin);
        if (!requireHost(pin, principal)) {
            return;
        }
        gameService.skipQuestion(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/question_skipped", true);
    }

    /**
     * End the game early.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin game pin
     */
    @MessageMapping("/end_game_early")
    public void endGameEarly(final String pin, final Principal principal) {
        logger.info("Ending game early: {}", pin);
        if (!requireHost(pin, principal)) {
            return;
        }
        gameService.setGameStatus(pin, GameStatus.ENDED);
        gameService.markGame(pin, com.quiz.darkhold.game.entity.GameStatus.FINISHED);
        messagingTemplate.convertAndSend("/topic/" + pin + "/game_ended", true);
    }

    /**
     * Kick a player from the game.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param message format: "pin:username"
     */
    @MessageMapping("/kick_player")
    public void kickPlayer(final String message, final Principal principal) {
        // Expected format: "pin:username"
        String[] parts = message.split(":", MESSAGE_PARTS_KICK_PLAYER);
        if (parts.length != MESSAGE_PARTS_KICK_PLAYER) {
            logger.warn("Invalid kick message format: {}", message);
            return;
        }
        String pin = parts[0];
        String username = parts[1];
        if (!requireHost(pin, principal)) {
            return;
        }
        logger.info("Kicking player {} from game {}", username, pin);
        boolean kicked = gameService.kickPlayer(pin, username);
        String result = kicked ? username : null;
        messagingTemplate.convertAndSend("/topic/" + pin + "/player_kicked", result);
    }

    /**
     * Get current participant count.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin game pin
     */
    @MessageMapping("/participant_count")
    public void getParticipantCount(final String pin, final Principal principal) {
        if (!requireHost(pin, principal)) {
            return;
        }
        int count = gameService.getParticipantCount(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/participant_count", count);
    }

    // ==================== Team Mode WebSocket Endpoints ====================

    /**
     * Assign a player to a team.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param message format: "pin:username:teamName"
     */
    @MessageMapping("/team/assign")
    public void assignToTeam(final String message, final Principal principal) {
        String[] parts = message.split(":", MESSAGE_PARTS_TEAM_ASSIGN);
        if (parts.length != MESSAGE_PARTS_TEAM_ASSIGN) {
            logger.warn("Invalid team assign message format: {}", message);
            return;
        }
        String pin = parts[0];
        String username = parts[1];
        String teamName = parts[2];
        if (!requireHost(pin, principal)) {
            return;
        }

        logger.info("Assigning player {} to team {} in game {}", username, teamName, pin);
        teamService.assignPlayerToTeam(pin, username, teamName);

        List<TeamInfo> teams = teamService.getTeams(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/team_update", teams);
    }

    /**
     * Get current team configuration.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin game pin
     */
    @MessageMapping("/team/list")
    public void getTeams(final String pin) {
        logger.info("Fetching teams for game: {}", pin);
        List<TeamInfo> teams = teamService.getTeams(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/team_list", teams);
    }

    /**
     * Fetch team scores.
     * Sends to PIN-scoped topic for concurrent game support.
     *
     * @param pin game pin
     */
    @MessageMapping("/team/scores")
    public void getTeamScores(final String pin) {
        logger.info("Fetching team scores for game: {}", pin);
        Map<String, Integer> teamScores = teamService.calculateTeamScores(pin);
        messagingTemplate.convertAndSend("/topic/" + pin + "/team_scores", teamScores);
    }

    /**
     * Host-only WebSocket actions are ignored (and logged) for anyone but the game's moderator.
     */
    private boolean requireHost(final String pin, final Principal principal) {
        if (gameService.isModerator(pin, principal)) {
            return true;
        }
        logger.warn("Ignoring host-only message for game {} from {}", CommonUtils.sanitizedString(pin),
                principal != null ? CommonUtils.sanitizedString(principal.getName()) : null);
        return false;
    }
}
