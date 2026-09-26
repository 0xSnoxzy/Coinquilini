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
