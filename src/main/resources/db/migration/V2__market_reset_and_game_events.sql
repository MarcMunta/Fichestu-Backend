ALTER TABLE game_sessions
    ADD COLUMN winner_token_alias VARCHAR(20) NULL;

ALTER TABLE game_sessions
    ADD COLUMN impact_applied BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS market_reset_audit (
    reset_id INT AUTO_INCREMENT PRIMARY KEY,
    business_date DATE NOT NULL UNIQUE,
    zone_id VARCHAR(50) NOT NULL,
    summary TEXT,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS game_session_events (
    event_id INT AUTO_INCREMENT PRIMARY KEY,
    match_id INT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_game_session_events_match
        FOREIGN KEY (match_id) REFERENCES game_sessions(match_id) ON DELETE CASCADE
);
