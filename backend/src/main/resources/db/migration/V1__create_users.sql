-- Contas de usuário (data-model.md §1)
CREATE TABLE app_user (
    id            uuid         PRIMARY KEY,
    name          varchar(80)  NOT NULL,
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);
