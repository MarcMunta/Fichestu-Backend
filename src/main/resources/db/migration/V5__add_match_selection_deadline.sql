ALTER TABLE game_sessions
    ADD COLUMN selection_deadline TIMESTAMP NULL;

CREATE INDEX idx_game_sessions_selection_deadline
    ON game_sessions (selection_deadline);
