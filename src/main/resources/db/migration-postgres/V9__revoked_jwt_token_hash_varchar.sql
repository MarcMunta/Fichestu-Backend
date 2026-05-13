ALTER TABLE revoked_jwt_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);
