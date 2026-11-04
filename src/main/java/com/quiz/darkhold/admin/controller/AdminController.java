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

    public AdminController(final com.quiz.darkhold.user.repository.UserRepository users,
                           final com.quiz.darkhold.challenge.repository.ChallengeRepository challenges) {
        this.users = users;
        this.challenges = challenges;
    }

    @GetMapping("/users/{id}/quizzes")
    public String userQuizzes(@org.springframework.web.bind.annotation.PathVariable final Long id,
                              final Model model) {
        model.addAttribute("owner", users.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND)));
        model.addAttribute("quizzes", challenges.findByChallengeOwner(id));
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
        // Statistics will be added later via service layer
        // For now, showing the dashboard structure
        model.addAttribute("totalUsers", 0);
        model.addAttribute("activeGames", 0);
        model.addAttribute("totalChallenges", 0);
        model.addAttribute("gamesPlayed", 0);

        return "admin/dashboard";
    }
}
