# Data Model: Participantes Dependentes

Mudanças no banco da feature 004 (migração `V8__dependents.sql`). Tudo o que não aparece aqui
continua como nas features 001–003. Decisões em [research.md](./research.md) (R33, R36, R37).

## 1. `app_user` — Conta ou dependente (alterada)

| Coluna | Tipo | Mudança |
|--------|------|---------|
| `kind` | `varchar(10) NOT NULL DEFAULT 'ACCOUNT'` | nova; `ACCOUNT` ou `DEPENDENT` |
| `email` | `varchar(254) NULL` | passa a aceitar nulo |
| `password_hash` | `varchar(100) NULL` | passa a aceitar nulo |

- `CHECK (kind IN ('ACCOUNT', 'DEPENDENT'))`.
- `CHECK ((kind = 'ACCOUNT' AND email IS NOT NULL AND password_hash IS NOT NULL)
  OR (kind = 'DEPENDENT' AND email IS NULL AND password_hash IS NULL))`: dependente nunca tem
  e-mail nem senha (FR-002), conta sempre tem os dois.
- `uq_app_user_email` continua; vários `NULL` não conflitam.
- `name` (2–80, sem espaços nas pontas) é o nome do dependente (FR-001).
- Na entidade `AppUser`: fábrica `AppUser.dependent(name, createdAt)`, `isDependent()`;
  `getEmail()`/`getPasswordHash()` podem ser nulos só para dependente. O login continua buscando por
  e-mail; um dependente nunca é encontrado (não tem e-mail) e nunca tem sessão.

**Ciclo de vida do dependente**: nasce junto com a única participação dele (FR-008); é apagado
quando a participação acaba (remoção, saída do responsável), **exceto** se um `exit_request` do
próprio dependente o referencia — aí fica só como registro (R37).

## 2. `group_member` — Participação (alterada)

| Coluna | Tipo | Mudança |
|--------|------|---------|
| `guardian_id` | `uuid NULL` | nova; o responsável, se a participação é de um dependente |

- `FOREIGN KEY (group_id, guardian_id) REFERENCES group_member (group_id, user_id) ON DELETE CASCADE`:
  o responsável participa do mesmo grupo, e a participação dos dependentes some junto com a dele
  (FR-016). Em cadeia, o `wish_item` dos dependentes também some (FK da 003).
- `CHECK (guardian_id IS NULL OR guardian_id <> user_id)`.
- `CREATE INDEX ix_group_member_guardian ON group_member (group_id, guardian_id) WHERE guardian_id IS NOT NULL`
  — dependentes de um responsável no grupo (limite de 5, confirmação, resultados).
- `confirmed_at` do dependente é espelho do responsável (R36).
- Regras na aplicação (com o grupo travado pelo `GroupLock`): `guardian_id` só aponta para
  participação de conta (`kind = 'ACCOUNT'`); no máximo 5 participações por
  `(group_id, guardian_id)` (FR-003); um dependente nunca é responsável (não há dependente de
  dependente — e ele nem tem sessão para pedir isso).

## 3. `exit_request` — Pedido de saída (alterada)

| Coluna | Tipo | Mudança |
|--------|------|---------|
| `requested_by` | `uuid NOT NULL REFERENCES app_user (id)` | nova; quem pediu (o próprio usuário ou o responsável) |

- Migração: `ADD COLUMN requested_by uuid NULL` → `UPDATE exit_request SET requested_by = user_id` →
  `SET NOT NULL`.
- `ix_exit_request_unseen` é recriado sobre `(requested_by) WHERE status <> 'PENDING' AND decision_seen_at IS NULL`:
  o aviso da decisão vai para quem pediu (R37).
- `CREATE INDEX ix_exit_request_approved_by ON exit_request (group_id, requested_by) WHERE status = 'APPROVED'`
  — regra "não readicionar dependentes" (Clarifications Q3).

## 4. Sem mudança

- `draw_assignment`: o dependente é um `giver_id`/receiver como outro qualquer; o par continua
  cifrado com AAD `group_id‖giver_id`. Nenhuma coluna nova liga par a responsável.
- `wish_item`: itens do dependente com `user_id` = id do dependente.
- `group_exit_ban`: criado para o dependente na aprovação, como para qualquer participante.
- `secret_group`: sem mudança (o limite de 100 já conta todas as participações).

## Quem lê o quê

| Dado | Quem pode ler | Por onde |
|------|---------------|----------|
| Nome do dependente e do responsável | membros do grupo | `GroupDetail.participants` |
| Lista de desejos do dependente (edição) | só o responsável | `GroupDetail.myDependents[].wishlist` |
| Lista de desejos do dependente (leitura) | quem tirou o dependente | `my-secret-friend` (R27) |
| Resultado do dependente | só o responsável | `my-dependents/secret-friends` (R38) |
| Pedido de saída do dependente | responsável; organizador (pendentes) | `myDependents[].exitRequest`; `pendingExitRequests` |
| Aviso da decisão | quem pediu (`requested_by`) | `exit-request-notices` |

## Validações (espelhadas no frontend com zod)

| Campo | Regra | Erro |
|-------|-------|------|
| `name` do dependente | obrigatório; após `strip`, 2 a 80 caracteres | `400 validation-error` |
| dependentes por responsável | ≤ 5 por grupo | `409 dependents-limit` |
| participantes do grupo | ≤ 100, contando dependentes | `409 group-full` |
| situação do grupo ao adicionar/renomear/remover | `OPEN` | `409 group-already-drawn` / `409 group-finished` |
| saída aprovada de dependente seu no grupo | não pode adicionar mais | `409 dependents-locked` |
| sorteio | existe atribuição válida (R35) | `422 no-valid-draw` |

## Migração

`V8__dependents.sql`, nesta ordem: colunas e `CHECK`s de `app_user`; `guardian_id`, FK, `CHECK` e
índice de `group_member`; `requested_by` (com preenchimento) e índices de `exit_request`. Nenhum
dado existente muda de significado: todas as contas viram `ACCOUNT`, nenhuma participação tem
responsável, e cada pedido antigo foi pedido pelo próprio usuário.
