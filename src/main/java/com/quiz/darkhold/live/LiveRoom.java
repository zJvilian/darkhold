package com.quiz.darkhold.live;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "live_room")
public class LiveRoom implements Persistable<String> {
    @Id
    private String pin;
    @Column(name = "host_name", nullable = false)
    private String hostName;
    @Column(name = "state_json", nullable = false, columnDefinition = "TEXT")
    private String stateJson;
    @Column(name = "expires_at", nullable = false)
    private long expiresAt;
    @Transient
    private boolean newRoom = true;

    @Override
    public String getId() {
        return pin;
    }

    @Override
    public boolean isNew() {
        return newRoom;
    }

    @PostLoad
    @PostPersist
    public void markPersisted() {
        newRoom = false;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(final String pin) {
        this.pin = pin;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(final String hostName) {
        this.hostName = hostName;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(final String stateJson) {
        this.stateJson = stateJson;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(final long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
