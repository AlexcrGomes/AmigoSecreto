-- Grupos e participantes (data-model.md §2 e §3)
CREATE TABLE secret_group (
    id           uuid         PRIMARY KEY,
    name         varchar(60)  NOT NULL,
    code         char(8)      NOT NULL,
    organizer_id uuid         NOT NULL REFERENCES app_user (id),
    status       varchar(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DRAWN')),
    created_at   timestamptz  NOT NULL,
    drawn_at     timestamptz  NULL,
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_secret_group_code UNIQUE (code),
    CONSTRAINT ck_secret_group_drawn CHECK ((status = 'DRAWN') = (drawn_at IS NOT NULL))
);

CREATE TABLE group_member (
    group_id  uuid        NOT NULL REFERENCES secret_group (id),
    user_id   uuid        NOT NULL REFERENCES app_user (id),
    joined_at timestamptz NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

CREATE INDEX ix_group_member_user ON group_member (user_id);
