-- Participantes dependentes (feature 004, data-model.md, research R33 e R37).
-- O dependente é um app_user sem e-mail e sem senha, com uma única participação ligada à do responsável.
-- Nenhuma coluna liga par do sorteio a responsável (Constituição I).

-- 1. Conta ou dependente; dependente nunca tem credenciais (FR-002)
ALTER TABLE app_user
    ADD COLUMN kind varchar(10) NOT NULL DEFAULT 'ACCOUNT',
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD CONSTRAINT ck_app_user_kind CHECK (kind IN ('ACCOUNT', 'DEPENDENT')),
    ADD CONSTRAINT ck_app_user_credentials CHECK (
        (kind = 'ACCOUNT' AND email IS NOT NULL AND password_hash IS NOT NULL)
        OR (kind = 'DEPENDENT' AND email IS NULL AND password_hash IS NULL));

-- 2. O responsável participa do mesmo grupo; os dependentes saem junto com ele (FR-016)
ALTER TABLE group_member
    ADD COLUMN guardian_id uuid NULL,
    ADD CONSTRAINT fk_group_member_guardian FOREIGN KEY (group_id, guardian_id)
        REFERENCES group_member (group_id, user_id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_group_member_guardian CHECK (guardian_id IS NULL OR guardian_id <> user_id);

-- Dependentes de um responsável no grupo (limite de 5, confirmação, resultados)
CREATE INDEX ix_group_member_guardian ON group_member (group_id, guardian_id) WHERE guardian_id IS NOT NULL;

-- 3. Quem pediu a saída: o próprio usuário ou o responsável (R37)
ALTER TABLE exit_request ADD COLUMN requested_by uuid NULL REFERENCES app_user (id);
UPDATE exit_request SET requested_by = user_id;
ALTER TABLE exit_request ALTER COLUMN requested_by SET NOT NULL;

-- O aviso da decisão vai para quem pediu
DROP INDEX ix_exit_request_unseen;
CREATE INDEX ix_exit_request_unseen ON exit_request (requested_by)
    WHERE status <> 'PENDING' AND decision_seen_at IS NULL;

-- Regra "não readicionar dependentes" depois de uma saída aprovada (Clarifications Q3)
CREATE INDEX ix_exit_request_approved_by ON exit_request (group_id, requested_by) WHERE status = 'APPROVED';
