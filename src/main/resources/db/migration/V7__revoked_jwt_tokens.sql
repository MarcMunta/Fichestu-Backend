CREATE TABLE IF NOT EXISTS revoked_jwt_tokens (
    revoked_token_id INT AUTO_INCREMENT PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    user_id INT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(50),
    INDEX idx_revoked_jwt_tokens_hash_expires (token_hash, expires_at),
    INDEX idx_revoked_jwt_tokens_expires_at (expires_at),
    CONSTRAINT fk_revoked_jwt_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
);
