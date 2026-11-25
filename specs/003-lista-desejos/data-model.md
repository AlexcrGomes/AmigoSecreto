# Data Model: Lista de Desejos

Mudanças em relação às features 001 e 002. Tudo o que não aparece aqui continua igual.
Migração: `V7__wishlist_and_gift_value.sql`.

## 1. `secret_group` — Grupo (alterada)

| Campo novo | Tipo | Regras |
|------------|------|--------|
| `gift_value` | `integer` NULL | valor do presente, em reais inteiros; `CHECK (gift_value BETWEEN 1 AND 100000)`; NULL = não informado |

- Informado, alterado ou removido só com `status = 'OPEN'` (FR-020, FR-022, R30). Com `DRAWN`, o
  mesmo valor é aceito e outro é recusado; com `FINISHED`, nada muda (como os demais detalhes).
- Alterar o valor **não** muda `details_version` nem as confirmações (FR-023).
- Grupos existentes: `NULL` (FR-019).

## 2. `group_member` — Participação (alterada)

| Campo novo | Tipo | Regras |
|------------|------|--------|
| `wishlist_updated_at` | `timestamptz` NULL | última inclusão, edição ou remoção de item da lista do membro; NULL = nunca alterou |

- A tela de quem tirou o membro mostra "Atualizada em dd/mm" só quando
  `wishlist_updated_at > secret_group.drawn_at` (FR-015a, R27).

## 3. `wish_item` — Item da lista de desejos (nova)

| Campo | Tipo | Regras |
|-------|------|--------|
| `id` | `uuid` PK | |
| `group_id` | `uuid` NOT NULL | |
| `user_id` | `uuid` NOT NULL | dono da lista |
| `description` | `varchar(120)` NOT NULL | 1 a 120 caracteres, sem só espaços; texto simples (FR-002, FR-008) |
| `url` | `varchar(500)` NULL | `http://` ou `https://`, absoluto, com host (FR-003, R29) |
| `created_at` | `timestamptz` NOT NULL | ordem de cadastro (FR-007) |

- FK `(group_id, user_id) → group_member (group_id, user_id) ON DELETE CASCADE`: a lista some com
  a participação (FR-018).
- Índice `ix_wish_item_owner (group_id, user_id, created_at)`: lista do dono na ordem.
- No máximo 5 itens por `(group_id, user_id)` (FR-001), verificado no serviço com o grupo
  travado (R28).
- Nenhuma coluna referencia o sorteio (`draw_assignment`): saber quem vê a lista depende só do
  par do leitor, resolvido no `SecretFriendService` (R27).

## Quem lê o quê

| Dado | Quem pode ler | Por onde |
|------|---------------|----------|
| Itens da própria lista | o dono | `GroupDetail.myWishlist` e as respostas das rotas de edição |
| Itens da lista de outra pessoa | só quem a tirou, depois do sorteio | `GET /groups/{id}/my-secret-friend` (`wishlist`) |
| `gift_value` | todos os participantes | `GroupDetail.eventDetails.giftValue` |
| Se alguém viu a lista | ninguém | não é registrado (FR-015) |

## Validações (espelhadas no frontend com zod)

| Campo | Regra | Mensagem (pt-BR) |
|-------|-------|------------------|
| Descrição do item | obrigatória, 1–120 caracteres | "Descreva a sugestão (até 120 caracteres)." |
| Link do item | opcional; `http://` ou `https://`; até 500 caracteres | "Informe um link que comece com http:// ou https://." |
| Itens por lista | no máximo 5 | "A lista tem no máximo 5 sugestões." |
| Valor do presente | opcional; inteiro de 1 a 100.000 | "Informe um valor inteiro entre R$ 1 e R$ 100.000." |
| Valor do presente após o sorteio | não muda | "O valor do presente não muda depois do sorteio." |

## Migração

| Versão | Conteúdo |
|--------|----------|
| `V7__wishlist_and_gift_value.sql` | `secret_group.gift_value` com o `CHECK`; `group_member.wishlist_updated_at`; tabela `wish_item` com FK em cascata e índice |
