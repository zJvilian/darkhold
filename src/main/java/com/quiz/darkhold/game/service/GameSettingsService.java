package com.quiz.darkhold.game.service;

import com.quiz.darkhold.game.entity.GameSettings;
import com.quiz.darkhold.game.repository.GameSettingsRepository;
import com.quiz.darkhold.init.GameConfig;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Persists game defaults edited on the Game Management page and applies them to GameConfig,
 * which every game reads at runtime.
 */
@Service
public class GameSettingsService {
    public static final int MIN_TIMER = 5;
    public static final int MAX_TIMER = 240;
    public static final int MIN_PIN = 4;
    public static final int MAX_PIN = 8;

    private final GameSettingsRepository repository;
    private final GameConfig config;

    public GameSettingsService(final GameSettingsRepository repository, final GameConfig config) {
        this.repository = repository;
        this.config = config;
    }

    /** Saved settings override application.properties once the app is up. */
    @EventListener(ApplicationReadyEvent.class)
    public void applySaved() {
        repository.findById(GameSettings.SINGLETON_ID).ifPresent(s -> {
            config.setTimerSeconds(s.getTimerSeconds());
            config.setPinLength(s.getPinLength());
        });
    }

    /**
     * Validate, persist and apply new defaults.
     *
     * @return false if a value is out of range (nothing is changed)
     */
    public boolean update(final int timerSeconds, final int pinLength) {
        if (timerSeconds < MIN_TIMER || timerSeconds > MAX_TIMER || pinLength < MIN_PIN || pinLength > MAX_PIN) {
            return false;
        }
        GameSettings settings = repository.findById(GameSettings.SINGLETON_ID).orElseGet(GameSettings::new);
        settings.setTimerSeconds(timerSeconds);
        settings.setPinLength(pinLength);
        repository.save(settings);
        config.setTimerSeconds(timerSeconds);
        config.setPinLength(pinLength);
        return true;
    }
}
