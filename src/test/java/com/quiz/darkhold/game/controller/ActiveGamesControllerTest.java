package com.quiz.darkhold.game.controller;

import com.quiz.darkhold.challenge.repository.ChallengeRepository;
import com.quiz.darkhold.game.entity.Game;
import com.quiz.darkhold.live.LiveQuizService;
import com.quiz.darkhold.preview.repository.CurrentGame;
import com.quiz.darkhold.preview.service.PreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ActiveGamesControllerTest {

    private MockMvc mockMvc;
    private PreviewService previewService;
    private CurrentGame currentGame;
    private ChallengeRepository challengeRepository;
    private LiveQuizService liveQuizService;

    @BeforeEach
    void setUp() {
        previewService = mock(PreviewService.class);
        currentGame = mock(CurrentGame.class);
        challengeRepository = mock(ChallengeRepository.class);
        liveQuizService = mock(LiveQuizService.class);

        ActiveGamesController controller = new ActiveGamesController(
                previewService, currentGame, challengeRepository, liveQuizService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void showActiveGamesReturnsViewWithAttributes() throws Exception {
        final Principal principal = () -> "moderator@test.com";
        Game game = new Game();
        game.setPin("12345");
        game.setGameStatus("WAITING");
        when(previewService.getActiveGamesForModerator("moderator@test.com"))
                .thenReturn(List.of(game));
        when(liveQuizService.roomsForHost(anyString())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/my-active-games").principal(principal))
                .andExpect(status().isOk())
                .andExpect(view().name("activegames"))
                .andExpect(model().attributeExists("activeGames"))
                .andExpect(model().attributeExists("liveRooms"));
    }

    @Test
    void terminateGameInvokesServicesAndRedirects() throws Exception {
        final Principal principal = () -> "moderator@test.com";
        when(liveQuizService.terminate(eq("625583"), anyString(), anyBoolean())).thenReturn(true);

        mockMvc.perform(post("/active-games/terminate")
                        .param("pin", "625583")
                        .principal(principal))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-active-games"));

        verify(liveQuizService).terminate(eq("625583"), anyString(), anyBoolean());
    }

    @Test
    void terminateAllInvokesServicesAndRedirects() throws Exception {
        final Principal principal = () -> "moderator@test.com";

        mockMvc.perform(post("/active-games/terminate-all")
                        .principal(principal))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-active-games"));

        verify(liveQuizService).terminateAllForHost(anyString());
        verify(previewService).terminateAllGamesForModerator("moderator@test.com");
    }
}
