CREATE TABLE live_room (
    pin VARCHAR(6) PRIMARY KEY,
    host_name VARCHAR(255) NOT NULL,
    state_json TEXT NOT NULL,
    expires_at BIGINT NOT NULL
);
