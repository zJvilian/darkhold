package com.quiz.darkhold.options.controller;

import com.quiz.darkhold.game.service.GameService;
import com.quiz.darkhold.game.service.GameSettingsService;
import com.quiz.darkhold.init.GameConfig;
import com.quiz.darkhold.options.service.OptionsService;
import com.quiz.darkhold.preview.service.PreviewService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;

@Controller
public class OptionsController {
    private final Log log = LogFactory.getLog(OptionsController.class);
    private final OptionsService optionsService;
    private final PreviewService previewService;
    private final GameService gameService;
    private final GameSettingsService gameSettingsService;
    private final GameConfig gameConfig;

    public OptionsController(final OptionsService optionsService,
                             final PreviewService previewService,
                             final GameService gameService,
                             final GameSettingsService gameSettingsService,
                             final GameConfig gameConfig) {
        this.optionsService = optionsService;
        this.previewService = previewService;
        this.gameService = gameService;
        this.gameSettingsService = gameSettingsService;
        this.gameConfig = gameConfig;
    }

    /**
     * on to create challenge page.
     *
     * @return create challenge page
     */
    @PostMapping("/createChallenge")
    public String createChallenge() {
        log.info("Into the createChallenge method");
        return "challenge/createchallenge";
    }

    /**
     * on to view challenge page, with a view of challenges.
     *
     * @param model model
     * @param email user email
     * @return view challenge page
     */
    @PostMapping({"/viewChallenge", "/view_challenges", "/viewchallenges"})
    public String viewChallenges(final Model model,
                                 @AuthenticationPrincipal(expression = "email") final String email) {
        log.info("Into the viewChallenge method");
        log.info("email is : " +  email);
        model.addAttribute("challengeInfo", optionsService.populateChallengeInfo(email));
        return "challenge/viewchallenges";
    }

    @GetMapping({"/viewChallenge", "/challenge/view", "/view_challenges", "/viewchallenges"})
    public String viewChallengesPage(final Model model,
                                    @AuthenticationPrincipal(expression = "email") final String email) {
        return viewChallenges(model, email);
    }

    /**
     * Show the currently running challenge with all participants.
     *
     * @param model model to populate with challenge data
     * @return publish page
     */
    @PostMapping("/activeChallenge")
    public String activeChallenges(final Model model) {
        log.info("Into the activeChallenge method");
        com.quiz.darkhold.preview.model.PublishInfo publishInfo = previewService.getActiveChallenge();
        if (publishInfo != null && publishInfo.getPin() != null) {
            model.addAttribute("quizPin", publishInfo.getPin());
            model.addAttribute("user", publishInfo.getModerator());
            // Get all participants for the active game
            java.util.List<String> participants = gameService.getAllParticipants(publishInfo.getPin());
            model.addAttribute("participants", participants != null ? participants : Collections.emptyList());
            log.info("activeChallenges method, quizPin: " + publishInfo.getPin()
                    + ", participants: " + (participants != null ? participants.size() : 0));
        }
        return "challenge/publish";
    }



    /**
     * Game defaults page. Access is limited to ADMIN and GAME_MANAGER in SecurityConfig.
     *
     * @param model model
     * @return game management page
     */
    @GetMapping("/gameManagement")
    public String manageGame(final Model model) {
        model.addAttribute("timerSeconds", gameConfig.getTimerSeconds());
        model.addAttribute("pinLength", gameConfig.getPinLength());
        return "game/gameManagement";
    }

    /**
     * Save game defaults.
     *
     * @param timerSeconds default question timer
     * @param pinLength    PIN digits for new games
     * @return redirect back with a status flag
     */
    @PostMapping("/gameManagement")
    public String saveGameSettings(@RequestParam final int timerSeconds, @RequestParam final int pinLength) {
        return gameSettingsService.update(timerSeconds, pinLength)
                ? "redirect:/gameManagement?saved"
                : "redirect:/gameManagement?invalid";
    }
}
