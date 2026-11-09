package com.quiz.darkhold.game.service;

import com.quiz.darkhold.game.entity.GameSettings;
import com.quiz.darkhold.game.repository.GameSettingsRepository;
import com.quiz.darkhold.init.GameConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameSettingsServiceTest {
    private final GameSettingsRepository repository = mock(GameSettingsRepository.class);
    private final GameConfig config = new GameConfig();
    private final GameSettingsService service = new GameSettingsService(repository, config);

    @Test
    void updateSavesAndAppliesValidValues() {
        when(repository.findById(GameSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertTrue(service.update(30, 6));
        assertEquals(30, config.getTimerSeconds());
        assertEquals(6, config.getPinLength());
        verify(repository).save(any(GameSettings.class));
    }

    @Test
    void updateRejectsOutOfRangeValues() {
        assertFalse(service.update(4, 5));
        assertFalse(service.update(20, 9));
        assertEquals(20, config.getTimerSeconds());
        verify(repository, never()).save(any());
    }

    @Test
    void applySavedOverridesProperties() {
        GameSettings saved = new GameSettings();
        saved.setTimerSeconds(45);
        saved.setPinLength(7);
        when(repository.findById(GameSettings.SINGLETON_ID)).thenReturn(Optional.of(saved));
        service.applySaved();
        assertEquals(45, config.getTimerSeconds());
        assertEquals(7, config.getPinLength());
    }
}
