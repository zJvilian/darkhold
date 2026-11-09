package com.quiz.darkhold.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row store for admin-editable game defaults, mirrored into GameConfig.
 */
@Entity
@Table(name = "game_settings")
public class GameSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "timer_seconds")
    private int timerSeconds;

    @Column(name = "pin_length")
    private int pinLength;

    public Long getId() {
        return id;
    }

    public int getTimerSeconds() {
        return timerSeconds;
    }

    public void setTimerSeconds(final int timerSeconds) {
        this.timerSeconds = timerSeconds;
    }

    public int getPinLength() {
        return pinLength;
    }

    public void setPinLength(final int pinLength) {
        this.pinLength = pinLength;
    }
}
