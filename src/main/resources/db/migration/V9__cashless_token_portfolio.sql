INSERT INTO user_wallets (user_id, token_id, quantity)
SELECT
    u.user_id,
    t.token_id,
    ROUND((u.fiat_balance / token_counts.token_count) / t.current_price, 4) AS converted_quantity
FROM users u
JOIN tokens t
JOIN (
    SELECT COUNT(*) AS token_count
    FROM tokens
    WHERE current_price > 0
) token_counts
WHERE u.fiat_balance > 0
  AND t.current_price > 0
  AND token_counts.token_count > 0
ON DUPLICATE KEY UPDATE quantity = ROUND(user_wallets.quantity + VALUES(quantity), 4);

UPDATE users
SET fiat_balance = 0.00
WHERE fiat_balance <> 0.00;
