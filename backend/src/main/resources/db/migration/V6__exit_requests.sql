-- Pedido de saída depois do sorteio e registro de quem saiu (feature 002, data-model.md §3–§4, research
-- R18–R20). Nenhuma coluna guarda ou referencia pares do sorteio (FR-032). As FKs apontam para o grupo e o
-- usuário, não para group_member: a participação é apagada na aprovação e o pedido fica para o aviso.
CREATE TABLE exit_request (
    id               uuid         PRIMARY KEY,
    group_id         uuid         NOT NULL REFERENCES secret_group (id),
    user_id          uuid         NOT NULL REFERENCES app_user (id),
    status           varchar(10)  NOT NULL,
    requested_at     timestamptz  NOT NULL,
    decided_at       timestamptz  NULL,
    decision_seen_at timestamptz  NULL,
    CONSTRAINT ck_exit_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_exit_request_decided CHECK ((status = 'PENDING') = (decided_at IS NULL)),
    CONSTRAINT ck_exit_request_seen CHECK (decision_seen_at IS NULL OR status <> 'PENDING')
);

-- No máximo um pedido pendente por pessoa e grupo (FR-025)
CREATE UNIQUE INDEX uq_exit_request_pending ON exit_request (group_id, user_id) WHERE status = 'PENDING';

-- Avisos de decisão ainda não vistos (R20)
CREATE INDEX ix_exit_request_unseen ON exit_request (user_id) WHERE status <> 'PENDING' AND decision_seen_at IS NULL;

-- Pedidos pendentes de um grupo (tela do organizador e cancelamento em massa)
CREATE INDEX ix_exit_request_group_pending ON exit_request (group_id) WHERE status = 'PENDING';

-- Quem saiu depois do sorteio não entra de novo (FR-030, R19)
CREATE TABLE group_exit_ban (
    group_id   uuid        NOT NULL REFERENCES secret_group (id),
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
