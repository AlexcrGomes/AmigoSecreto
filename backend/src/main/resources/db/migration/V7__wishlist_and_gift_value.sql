-- Lista de desejos e valor do presente (feature 003, data-model.md, research R26 e R30).
-- Os itens não guardam nenhuma referência ao sorteio: quem pode ver a lista de alguém depende só do
-- par do leitor, resolvido pela aplicação (research R27).

-- Valor do presente do grupo, em reais inteiros (FR-020); NULL = não informado
ALTER TABLE secret_group
    ADD COLUMN gift_value integer NULL,
    ADD CONSTRAINT ck_secret_group_gift_value CHECK (gift_value BETWEEN 1 AND 100000);

-- Última inclusão, edição ou remoção de item da lista do membro ("Atualizada em", FR-015a)
ALTER TABLE group_member
    ADD COLUMN wishlist_updated_at timestamptz NULL;

-- Itens da lista de desejos; somem com a participação (FR-018)
CREATE TABLE wish_item (
    id          uuid          PRIMARY KEY,
    group_id    uuid          NOT NULL,
    user_id     uuid          NOT NULL,
    description varchar(120)  NOT NULL,
    url         varchar(500)  NULL,
    created_at  timestamptz   NOT NULL,
    CONSTRAINT fk_wish_item_member FOREIGN KEY (group_id, user_id)
        REFERENCES group_member (group_id, user_id) ON DELETE CASCADE
);

-- Lista do dono na ordem de cadastro (FR-007)
CREATE INDEX ix_wish_item_owner ON wish_item (group_id, user_id, created_at);
