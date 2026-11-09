-- Admin-editable game defaults (single row, id = 1)
CREATE TABLE IF NOT EXISTS game_settings (
    id            BIGINT PRIMARY KEY,
    timer_seconds INT NOT NULL,
    pin_length    INT NOT NULL
);
