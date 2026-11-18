-- Pares do sorteio (data-model.md §4, research R9). O receiver fica só cifrado (AES-256-GCM, AAD =
-- group_id‖giver_id): sem coluna em claro e sem índice sobre receiver_enc.
CREATE TABLE draw_assignment (
    group_id     uuid        NOT NULL,
    giver_id     uuid        NOT NULL,
    receiver_enc bytea       NOT NULL,
    created_at   timestamptz NOT NULL,
    PRIMARY KEY (group_id, giver_id),
    FOREIGN KEY (group_id, giver_id) REFERENCES group_member (group_id, user_id)
);
