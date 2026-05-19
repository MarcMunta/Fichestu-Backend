UPDATE tokens
SET current_price = ROUND(1 + (RAND() * 499), 2),
    last_update = CURRENT_TIMESTAMP;

INSERT INTO token_price_history (token_id, price)
SELECT token_id, current_price
FROM tokens;

DELETE FROM user_wallets;

INSERT INTO user_wallets (user_id, token_id, quantity)
SELECT
    u.user_id,
    t.token_id,
    ROUND((200.00 / token_counts.token_count) / t.current_price, 4) AS quantity
FROM users u
JOIN tokens t
JOIN (
    SELECT COUNT(*) AS token_count
    FROM tokens
    WHERE current_price > 0
) token_counts
WHERE t.current_price > 0
  AND token_counts.token_count > 0;

UPDATE users
SET fiat_balance = 0.00;
