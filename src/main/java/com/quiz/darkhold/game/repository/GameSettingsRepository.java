package com.quiz.darkhold.game.repository;

import com.quiz.darkhold.game.entity.GameSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameSettingsRepository extends JpaRepository<GameSettings, Long> {
}
