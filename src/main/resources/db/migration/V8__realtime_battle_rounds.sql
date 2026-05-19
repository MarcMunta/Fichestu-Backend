SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE game_sessions ADD COLUMN battle_round_deadline TIMESTAMP NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'game_sessions'
      AND column_name = 'battle_round_deadline'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE match_cards ADD COLUMN round_number INT NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'match_cards'
      AND column_name = 'round_number'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE match_cards ADD COLUMN target_user_id INT NULL',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'match_cards'
      AND column_name = 'target_user_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_match_cards_match_round ON match_cards (match_id, round_number)',
        'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'match_cards'
      AND index_name = 'idx_match_cards_match_round'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
