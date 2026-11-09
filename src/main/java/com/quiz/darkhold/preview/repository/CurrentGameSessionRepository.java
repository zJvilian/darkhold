package com.quiz.darkhold.preview.repository;

import com.quiz.darkhold.preview.entity.CurrentGameSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrentGameSessionRepository extends JpaRepository<CurrentGameSession, Long> {
    Optional<CurrentGameSession> findByPin(String pin);

    /**
     * Row-locking lookup for read-modify-write of the session's JSON columns.
     * Must be called inside a transaction.
     *
     * @param pin game PIN
     * @return the locked session
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CurrentGameSession s where s.pin = :pin")
    Optional<CurrentGameSession> lockByPin(String pin);

    List<CurrentGameSession> findByCreatedAtLessThan(Long createdAt);
}
