CREATE TABLE minigame_attempts (
    attempt_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    game_type VARCHAR(40) NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    entry_cost DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    reward_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    score INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_minigame_attempts_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_minigame_attempts_user_game_started
    ON minigame_attempts (user_id, game_type, payment_type, started_at);
