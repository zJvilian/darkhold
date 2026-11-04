package com.quiz.darkhold.live;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

public interface LiveRoomRepository extends JpaRepository<LiveRoom, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from LiveRoom room where room.pin = :pin")
    Optional<LiveRoom> lockByPin(String pin);

    List<LiveRoom> findByHostNameAndExpiresAtGreaterThanOrderByExpiresAtDesc(String hostName, long expiresAt);

    long deleteByExpiresAtLessThan(long expiresAt);
}
