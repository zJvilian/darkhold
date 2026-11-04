package com.quiz.darkhold.game.controller;

import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.game.entity.Game;
import com.quiz.darkhold.game.model.GameInfo;
import com.quiz.darkhold.preview.repository.CurrentGame;
import com.quiz.darkhold.preview.service.PreviewService;
import com.quiz.darkhold.live.LiveQuizService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for managing active games dashboard.
 */
@Controller
public class ActiveGamesController {

    private final PreviewService previewService;
    private final CurrentGame currentGame;
    private final ChallengeRepository challengeRepository;
    private final LiveQuizService liveQuizService;

    public ActiveGamesController(final PreviewService previewService,
                                 final CurrentGame currentGame,
                                 final ChallengeRepository challengeRepository,
                                 final LiveQuizService liveQuizService) {
        this.previewService = previewService;
        this.currentGame = currentGame;
        this.challengeRepository = challengeRepository;
        this.liveQuizService = liveQuizService;
    }

    /**
     * Display active games dashboard for the current moderator.
     *
     * @param model     Spring MVC model
     * @param principal authenticated user
     * @return view name
     */
    @GetMapping("/my-active-games")
    public String showActiveGames(final Model model, final Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        String moderator = principal.getName();
        List<Game> activeGames = previewService.getActiveGamesForModerator(moderator);
        List<GameInfo> gameInfos = convertToGameInfos(activeGames);

        model.addAttribute("activeGames", gameInfos);
        model.addAttribute("liveRooms", liveQuizService.roomsForHost(LiveQuizService.hostIdentity(principal)));
        model.addAttribute("moderator", moderator);
        return "activegames";
    }

    private List<GameInfo> convertToGameInfos(final List<Game> games) {
        List<GameInfo> gameInfos = new ArrayList<>();
        for (Game game : games) {
            GameInfo info = createGameInfo(game);
            gameInfos.add(info);
        }
        return gameInfos;
    }

    private GameInfo createGameInfo(final Game game) {
        GameInfo info = new GameInfo();
        info.setPin(game.getPin());
        info.setStatus(game.getGameStatus());
        info.setChallengeId(game.getChallengeId());
        info.setCreatedOn(game.getCreatedOn());
        info.setGameMode(game.getGameMode());
        setChallengeName(info, game.getChallengeId());
        setParticipantCount(info, game.getPin());
        return info;
    }

    /**
     * Terminate an active game session (live room or classic game).
     *
     * @param pin       game PIN
     * @param principal authenticated user
     * @return redirect to active games
     */
    @PostMapping("/active-games/terminate")
    public String terminateGame(@RequestParam("pin") final String pin, final Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        boolean admin = isAdmin(principal);
        String host = LiveQuizService.hostIdentity(principal);
        boolean liveTerminated = liveQuizService.terminate(pin, host, admin);
        if (!liveTerminated) {
            previewService.terminateGame(pin, principal.getName(), admin);
        }
        return "redirect:/my-active-games";
    }

    /**
     * Terminate all active games for the current user.
     *
     * @param principal authenticated user
     * @return redirect to active games
     */
    @PostMapping("/active-games/terminate-all")
    public String terminateAll(final Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        liveQuizService.terminateAllForHost(LiveQuizService.hostIdentity(principal));
        previewService.terminateAllGamesForModerator(principal.getName());
        return "redirect:/my-active-games";
    }

    private boolean isAdmin(final Principal principal) {
        return principal instanceof Authentication auth
                && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private void setChallengeName(final GameInfo info, final String challengeIdStr) {
        try {
            Long challengeId = Long.valueOf(challengeIdStr);
            challengeRepository.findById(challengeId)
                    .ifPresent(challenge -> info.setChallengeName(challenge.getTitle()));
        } catch (NumberFormatException ex) {
            info.setChallengeName("Unknown");
        }
    }

    private void setParticipantCount(final GameInfo info, final String pin) {
        List<String> participants = currentGame.getActiveUsersInGame(pin);
        int count = participants != null ? participants.size() - 1 : 0; // Exclude moderator
        info.setParticipantCount(count);
    }
}
