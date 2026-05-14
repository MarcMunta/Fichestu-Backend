ALTER TABLE game_sessions
    ADD COLUMN IF NOT EXISTS battle_round_deadline TIMESTAMP WITH TIME ZONE NULL;

ALTER TABLE match_cards
    ADD COLUMN IF NOT EXISTS round_number INTEGER NULL;

ALTER TABLE match_cards
    ADD COLUMN IF NOT EXISTS target_user_id INTEGER NULL;

CREATE INDEX IF NOT EXISTS idx_match_cards_match_round
    ON match_cards (match_id, round_number);
