ALTER TABLE match_cards
    ADD COLUMN round_number INT NOT NULL DEFAULT 1,
    ADD COLUMN selected_token_alias VARCHAR(20) NULL;

CREATE UNIQUE INDEX uq_match_cards_match_owner_round
    ON match_cards (match_id, owner_id, round_number);

CREATE UNIQUE INDEX uq_match_participants_match_ball
    ON match_participants (match_id, selected_ball_number);

UPDATE users
SET password_hash = '$2a$10$GVS5fIB81CNRlClCya6DqOBOzAG2NbY49QXRW3Lx8tE24EuGDBDO6'
WHERE email = 'admin@casino.com'
  AND password_hash = 'admin123';

UPDATE users
SET password_hash = '$2a$10$650tE8fA4IFakXRj1rGvhuziQ8CweurV6L2UsVy9XSqK3ThFJXnHa'
WHERE email = 'player@test.com'
  AND password_hash = 'pass123';
