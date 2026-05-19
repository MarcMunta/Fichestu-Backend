INSERT INTO tokens (name, color_code, current_price, last_update)
SELECT 'Ficha Gris', '#9CA3AF', 1.00, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM tokens
    WHERE LOWER(name) IN ('ficha gris', 'ficha incolora')
);

UPDATE tokens
SET color_code = '#9CA3AF',
    current_price = 1.00,
    last_update = CURRENT_TIMESTAMP
WHERE LOWER(name) IN ('ficha gris', 'ficha incolora');

UPDATE tokens
SET current_price = ROUND(1 + (RAND() * 499), 2),
    last_update = CURRENT_TIMESTAMP
WHERE LOWER(name) NOT IN ('ficha gris', 'ficha incolora');

INSERT INTO token_price_history (token_id, price)
SELECT token_id, current_price
FROM tokens;

DELETE FROM user_wallets;

INSERT INTO user_wallets (user_id, token_id, quantity)
SELECT
    u.user_id,
    grey.token_id,
    200.0000
FROM users u
CROSS JOIN (
    SELECT MIN(token_id) AS token_id
    FROM tokens
    WHERE LOWER(name) IN ('ficha gris', 'ficha incolora')
) grey
WHERE grey.token_id IS NOT NULL;

UPDATE users
SET fiat_balance = 0.00;

INSERT INTO market_reset_audit (business_date, zone_id, summary)
SELECT '2000-01-02', 'Europe/Madrid', 'Reset inicial grey token: todos los usuarios pasan a 200 FTC en ficha gris.'
WHERE NOT EXISTS (
    SELECT 1
    FROM market_reset_audit
    WHERE business_date = '2000-01-02'
);
