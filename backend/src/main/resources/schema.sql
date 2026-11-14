-- Schema iniziale per il Modulo 1: Utenti e Autenticazione (slide: Mapping in Java, E-R)
CREATE TABLE IF NOT EXISTS app_user (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER' NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    color VARCHAR(20) DEFAULT '#2563eb' NOT NULL,
    photo TEXT DEFAULT '' NOT NULL
);

-- Case: il codice invito è memorizzato in forma canonica (6 caratteri maiuscoli).
CREATE TABLE IF NOT EXISTS house (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    emoji VARCHAR(20) DEFAULT '🏠' NOT NULL,
    photo TEXT DEFAULT '' NOT NULL,
    invite_code VARCHAR(20) UNIQUE NOT NULL,
    archived BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT house_invite_code_format CHECK (invite_code ~ '^[A-Z0-9]{6}$')
);

-- Le appartenenze inattive restano disponibili come storico degli ex membri.
CREATE TABLE IF NOT EXISTS membership (
    id SERIAL PRIMARY KEY,
    house_id INT NOT NULL REFERENCES house(id) ON DELETE CASCADE,
    user_id INT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role VARCHAR(20) DEFAULT 'MEMBER' NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    CONSTRAINT membership_role CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT membership_user_house UNIQUE (user_id, house_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS membership_one_active_per_user
    ON membership (user_id) WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS membership_house_id ON membership (house_id);
