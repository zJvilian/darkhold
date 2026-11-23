package com.quiz.darkhold.analytics.controller;

import com.quiz.darkhold.analytics.entity.GameResult;
import com.quiz.darkhold.analytics.service.ResultService;
import com.quiz.darkhold.team.entity.TeamResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Controller for viewing past game results and analytics.
 * Moderators see their own games; admins see and manage every game.
 */
@Controller
public class PastGamesController {

    private static final Logger logger = LogManager.getLogger(PastGamesController.class);
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String PAST_GAMES_REDIRECT = "redirect:/past-games";
    private final ResultService resultService;

    public PastGamesController(final ResultService resultService) {
        this.resultService = resultService;
    }

    /**
     * Display past games for the current moderator, or all games for an admin.
     *
     * @param model          Spring MVC model
     * @param authentication authenticated user
     * @return view name
     */
    @GetMapping({"/past-games", "/pastgames"})
    public String showPastGames(final Model model, final Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        String moderator = authentication.getName();
        boolean admin = isAdmin(authentication);
        List<GameResult> pastGames = admin
                ? resultService.getAllGameResults()
                : resultService.getGameResultsByModerator(moderator);

        model.addAttribute("pastGames", pastGames);
        model.addAttribute("moderator", moderator);
        model.addAttribute("admin", admin);
        return "pastgames";
    }

    /**
     * Display detailed results for a specific game.
     *
     * @param id             game result ID
     * @param model          Spring MVC model
     * @param authentication authenticated user
     * @return view name
     */
    @GetMapping({"/game-result/{id}", "/gameresult/{id}"})
    public String showGameResult(@PathVariable final Long id, final Model model,
                                 final Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        GameResult gameResult = resultService.getGameResultById(id);
        if (gameResult == null) {
            logger.warn("Game result not found: {}", id);
            return PAST_GAMES_REDIRECT;
        }
        if (!canAccess(gameResult, authentication)) {
            logger.warn("User {} attempted to access game result for moderator {}",
                    authentication.getName(), gameResult.getModerator());
            return PAST_GAMES_REDIRECT;
        }

        model.addAttribute("gameResult", gameResult);
        model.addAttribute("teamResults", resultService.getTeamResults(id));
        return "gameresult";
    }

    /**
     * Delete a saved game result.
     *
     * @param id             game result ID
     * @param authentication authenticated user
     * @return redirect back to the list
     */
    @PostMapping({"/game-result/{id}/delete", "/gameresult/{id}/delete"})
    public String deleteGameResult(@PathVariable final Long id, final Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        GameResult gameResult = resultService.getGameResultById(id);
        if (gameResult != null && canAccess(gameResult, authentication)) {
            resultService.deleteGameResult(id);
            logger.info("Game result {} deleted by {}", id, authentication.getName());
        }
        return PAST_GAMES_REDIRECT;
    }

    /**
     * Export game results to CSV.
     *
     * @param id             game result ID
     * @param authentication authenticated user
     * @return the CSV file, or 401/403/404 when it cannot be exported
     */
    @GetMapping({"/game-result/{id}/export-csv", "/gameresult/{id}/export-csv"})
    public ResponseEntity<byte[]> exportGameResultCsv(@PathVariable final Long id,
                                                      final Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GameResult gameResult = resultService.getGameResultById(id);
        if (gameResult == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccess(gameResult, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String filename = "game-result-" + gameResult.getPin().replaceAll("[^A-Za-z0-9_-]", "_") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(buildCsv(gameResult, resultService.getTeamResults(id)).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isAdmin(final Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()));
    }

    private static boolean canAccess(final GameResult gameResult, final Authentication authentication) {
        return isAdmin(authentication) || authentication.getName().equals(gameResult.getModerator());
    }

    @SuppressWarnings("PMD.ExcessiveMethodLength")
    private String buildCsv(final GameResult gameResult, final List<TeamResult> teamResults) {
        StringBuilder csv = new StringBuilder();

        csv.append("Game Results Export\n\n");
        csv.append("PIN,").append(cell(gameResult.getPin())).append('\n');
        csv.append("Quiz,").append(cell(gameResult.getChallengeName())).append('\n');
        csv.append("Mode,").append(cell(gameResult.getGameMode())).append('\n');
        csv.append("Completed,").append(gameResult.getCompletedAt().format(CSV_DATE)).append('\n');
        csv.append("Duration (minutes),").append(gameResult.getDurationMinutes()).append('\n');
        csv.append("Total Questions,").append(gameResult.getTotalQuestions()).append('\n');
        csv.append("Participants,").append(gameResult.getParticipantCount()).append("\n\n");

        csv.append("Participant Results\n");
        csv.append("Rank,Username,Score,Correct,Incorrect,Max Streak,Accuracy %,Team\n");
        gameResult.getParticipantResults().forEach(pr -> csv
                .append(pr.getFinalRank()).append(',')
                .append(cell(ResultService.displayName(pr.getUsername()))).append(',')
                .append(pr.getFinalScore()).append(',')
                .append(pr.getCorrectAnswers()).append(',')
                .append(pr.getIncorrectAnswers()).append(',')
                .append(pr.getMaxStreak()).append(',')
                .append(String.format(Locale.ROOT, "%.1f", pr.getAccuracyPercentage())).append(',')
                .append(cell(pr.getTeamName())).append('\n'));

        if (!teamResults.isEmpty()) {
            csv.append("\nTeam Standings\n");
            csv.append("Rank,Team,Score,Members,Average Score Per Member\n");
            teamResults.forEach(tr -> csv
                    .append(tr.getFinalRank()).append(',')
                    .append(cell(tr.getTeamName())).append(',')
                    .append(tr.getFinalScore()).append(',')
                    .append(tr.getMemberCount()).append(',')
                    .append(tr.getAverageScorePerMember()).append('\n'));
        }

        csv.append("\nQuestion Results\n");
        csv.append("Number,Question,Type,Correct,Incorrect,Timeout,Success Rate %,Difficulty\n");
        gameResult.getQuestionResults().forEach(qr -> csv
                .append(qr.getQuestionNumber()).append(',')
                .append(cell(qr.getQuestionText())).append(',')
                .append(cell(qr.getQuestionType())).append(',')
                .append(qr.getCorrectCount()).append(',')
                .append(qr.getIncorrectCount()).append(',')
                .append(qr.getTimeoutCount()).append(',')
                .append(String.format(Locale.ROOT, "%.1f", qr.getSuccessRatePercentage())).append(',')
                .append(cell(qr.getDifficultyLevel())).append('\n'));
        return csv.toString();
    }

    /**
     * One RFC 4180 text field. Spreadsheets run a leading "=", "+", "-" or "@" as a formula, so those get a
     * leading apostrophe; a player can pick any nickname.
     */
    private static String cell(final Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
