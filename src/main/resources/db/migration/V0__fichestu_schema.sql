-- =======================================================
-- BASE DE DATOS: FICHESTU
-- Motor: MySQL / MariaDB
-- =======================================================

-- -------------------------------------------------------
-- 1. TABLA DE USUARIOS (Con Roles)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    profile_pic_url TEXT,
    fiat_balance DECIMAL(15, 2) DEFAULT 0.00,
    role VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------------------------------------
-- 2. TABLA DE INSIGNIAS (Perfil)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS badges (
    badge_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    icon_url TEXT,
    description TEXT
);

CREATE TABLE IF NOT EXISTS user_badges (
    user_id INT,
    badge_id INT,
    awarded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, badge_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (badge_id) REFERENCES badges(badge_id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- 3. TABLA DE FICHAS (Tokens tipo Cripto)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS tokens (
    token_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    color_code VARCHAR(7),
    current_price DECIMAL(15, 2) DEFAULT 10.00,
    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_wallets (
    user_id INT,
    token_id INT,
    quantity DECIMAL(15, 4) DEFAULT 0.0000,
    PRIMARY KEY (user_id, token_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (token_id) REFERENCES tokens(token_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS token_price_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    token_id INT,
    price DECIMAL(15, 2) NOT NULL,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (token_id) REFERENCES tokens(token_id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- 4. SISTEMA DE JUEGO (Minijuegos 1 y 2)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS game_sessions (
    match_id INT AUTO_INCREMENT PRIMARY KEY,
    status VARCHAR(20) DEFAULT 'WAITING',
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL,
    winner_id INT DEFAULT NULL,
    FOREIGN KEY (winner_id) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS match_participants (
    match_id INT,
    user_id INT,
    selected_ball_number INT DEFAULT NULL,
    multiplier_won DECIMAL(10, 2) DEFAULT 1.00,
    current_hp INT DEFAULT 50,
    is_alive BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (match_id, user_id),
    FOREIGN KEY (match_id) REFERENCES game_sessions(match_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS match_cards (
    card_id INT AUTO_INCREMENT PRIMARY KEY,
    match_id INT,
    owner_id INT,
    card_type VARCHAR(20),
    card_value INT,
    is_used BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (match_id) REFERENCES game_sessions(match_id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- 5. LOGS Y TRANSACCIONES (Auditoria)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions_log (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    type VARCHAR(50),
    amount_fiat DECIMAL(15, 2),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- -------------------------------------------------------
-- 6. DATOS DE EJEMPLO (SEED DATA)
-- -------------------------------------------------------
INSERT INTO tokens (name, color_code, current_price) VALUES
('Ficha Roja', '#FF0000', 50.00),
('Ficha Azul', '#0000FF', 25.00),
('Ficha Verde', '#00FF00', 10.00),
('Ficha Dorada', '#FFD700', 100.00);

INSERT INTO users (username, email, password_hash, role, fiat_balance)
VALUES ('SuperAdmin', 'admin@fichestu.local', 'admin123', 'ADMIN', 999999.99);

INSERT INTO users (username, email, password_hash, role, fiat_balance)
VALUES ('Jugador1', 'player@test.com', 'pass123', 'USER', 100.00);
