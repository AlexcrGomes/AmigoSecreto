-- Detalhes do evento, grupo finalizado e confirmação de participação (feature 002, data-model.md §1–§2).
-- Datas em UTC (research R13): a data e o horário informados em horário de Brasília viram instantes.

ALTER TABLE secret_group
    ADD COLUMN description     varchar(500) NULL,
    ADD COLUMN location        varchar(120) NULL,
    ADD COLUMN event_starts_at timestamptz  NULL,
    ADD COLUMN event_has_time  boolean      NULL,
    ADD COLUMN finalizes_at    timestamptz  NULL,
    ADD COLUMN details_version int          NOT NULL DEFAULT 1,
    ADD COLUMN finished_at     timestamptz  NULL,
    ADD COLUMN draw_undone_at  timestamptz  NULL;

-- A restrição de status da V3 foi declarada na coluna e recebeu o nome padrão do PostgreSQL
ALTER TABLE secret_group DROP CONSTRAINT secret_group_status_check;
ALTER TABLE secret_group
    ADD CONSTRAINT ck_secret_group_status CHECK (status IN ('OPEN', 'DRAWN', 'FINISHED'));

ALTER TABLE secret_group DROP CONSTRAINT ck_secret_group_drawn;
ALTER TABLE secret_group
    ADD CONSTRAINT ck_secret_group_drawn CHECK ((status IN ('DRAWN', 'FINISHED')) = (drawn_at IS NOT NULL)),
    ADD CONSTRAINT ck_secret_group_finished CHECK ((status = 'FINISHED') = (finished_at IS NOT NULL)),
    -- Grupos antigos (R22) ficam sem nenhum detalhe; os novos têm todos
    ADD CONSTRAINT ck_secret_group_event CHECK (
        (location IS NULL AND event_starts_at IS NULL AND event_has_time IS NULL AND finalizes_at IS NULL)
        OR (location IS NOT NULL AND event_starts_at IS NOT NULL AND event_has_time IS NOT NULL
            AND finalizes_at IS NOT NULL)),
    ADD CONSTRAINT ck_secret_group_finish_needs_event CHECK (status <> 'FINISHED' OR finalizes_at IS NOT NULL);

-- Busca dos grupos sorteados cujo evento já passou (tarefa de finalização, R17)
CREATE INDEX ix_secret_group_finalizes ON secret_group (finalizes_at) WHERE status = 'DRAWN';

-- NULL = pendente; preenchido = confirmado (FR-008)
ALTER TABLE group_member ADD COLUMN confirmed_at timestamptz NULL;

-- Grupos antigos (R22): em grupos já sorteados a confirmação não muda mais nada (FR-016)...
UPDATE group_member m
SET confirmed_at = g.drawn_at
FROM secret_group g
WHERE g.id = m.group_id AND g.status = 'DRAWN';

-- ...e o organizador nasce confirmado, em qualquer grupo
UPDATE group_member m
SET confirmed_at = m.joined_at
FROM secret_group g
WHERE g.id = m.group_id AND g.organizer_id = m.user_id;
