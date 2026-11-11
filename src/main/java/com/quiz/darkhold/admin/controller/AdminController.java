package com.quiz.darkhold.admin.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for admin dashboard and system management.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {
    private final com.quiz.darkhold.user.repository.UserRepository users;
    private final com.quiz.darkhold.challenge.repository.ChallengeRepository challenges;
    private final com.quiz.darkhold.options.service.OptionsService optionsService;

    private final com.quiz.darkhold.game.repository.GameRepository games;
    private final com.quiz.darkhold.live.LiveQuizService liveQuizzes;
    private final com.quiz.darkhold.analytics.repository.GameResultRepository results;

    public AdminController(final com.quiz.darkhold.user.repository.UserRepository users,
                           final com.quiz.darkhold.challenge.repository.ChallengeRepository challenges,
                           final com.quiz.darkhold.options.service.OptionsService optionsService,
                           final com.quiz.darkhold.game.repository.GameRepository games,
                           final com.quiz.darkhold.live.LiveQuizService liveQuizzes,
                           final com.quiz.darkhold.analytics.repository.GameResultRepository results) {
        this.users = users;
        this.challenges = challenges;
        this.optionsService = optionsService;
        this.games = games;
        this.liveQuizzes = liveQuizzes;
        this.results = results;
    }

    @GetMapping("/users/{id}/quizzes")
    public String userQuizzes(@org.springframework.web.bind.annotation.PathVariable final Long id,
                              final Model model) {
        model.addAttribute("owner", users.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND)));
        model.addAttribute("quizzes", optionsService.summarize(challenges.findByChallengeOwner(id), id));
        return "admin/user-quizzes";
    }


    /**
     * Show admin dashboard with system statistics.
     *
     * @param model Spring MVC model
     * @return view name
     */
    @GetMapping({"", "/"})
    public String showAdminDashboard(final Model model) {
        model.addAttribute("totalUsers", users.count());
        model.addAttribute("activeGames", liveQuizzes.activeRoomCount() + games.findByGameStatusNot(
                com.quiz.darkhold.game.entity.GameStatus.FINISHED.name()).size());
        model.addAttribute("totalChallenges", challenges.count());
        model.addAttribute("gamesPlayed", results.count());

        return "admin/dashboard";
    }
}
