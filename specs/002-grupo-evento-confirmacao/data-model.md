# Data Model: Detalhes do Evento, Confirmação de Participação e Grupo Finalizado

**Feature**: `002-grupo-evento-confirmacao` | **Data**: 2026-11-18 | **Plan**: [plan.md](./plan.md)

Altera o [modelo da feature 001](../001-amigo-secreto-sorteio/data-model.md). Tudo o que não
aparece aqui continua igual. Schema alterado **somente** por migrações Flyway; datas em
`timestamptz` UTC (R13); IDs `uuid` gerados pela aplicação.

## Diagrama

```text
app_user 1 ──< group_member >── 1 secret_group ──< exit_request >── 1 app_user
   │            (confirmed_at)        │  (detalhes do evento,
   │                                  │   FINISHED, details_version)
   │ 1                                │
   ├──< draw_assignment >─────────────┤
   └──< group_exit_ban >──────────────┘
```

---

## 1. `secret_group` — Grupo (alterada)

Campos novos:

| Campo | Tipo | Regras |
|-------|------|--------|
| `description` | `varchar(500)` NULL | opcional; até 500 caracteres após `trim`; vazio vira `NULL` (FR-001) |
| `location` | `varchar(120)` NULL | obrigatório na criação; 1–120 caracteres após `trim` (FR-001, FR-002) |
| `event_starts_at` | `timestamptz` NULL | data (e horário) em Brasília convertidos para UTC; sem horário = meia-noite em Brasília (R13) |
| `event_has_time` | `boolean` NULL | `true` se o horário foi informado |
| `finalizes_at` | `timestamptz` NULL | `event_starts_at` se há horário; senão, meia-noite do dia seguinte em Brasília (R13, FR-017) |
| `details_version` | `int` NOT NULL DEFAULT 1 | +1 quando data, horário ou local mudam; a descrição não conta (R15) |
| `finished_at` | `timestamptz` NULL | quando o grupo foi finalizado |
| `draw_undone_at` | `timestamptz` NULL | quando o último sorteio foi desfeito; volta a `NULL` no novo sorteio. Mostra o aviso "o sorteio foi desfeito" a quem não estava online (Edge Case) |

Restrições alteradas ou novas:

- `status`: `CHECK (status IN ('OPEN','DRAWN','FINISHED'))`.
- `ck_secret_group_drawn` passa a ser `(status IN ('DRAWN','FINISHED')) = (drawn_at IS NOT NULL)`.
- `ck_secret_group_finished`: `(status = 'FINISHED') = (finished_at IS NOT NULL)`.
- `ck_secret_group_event`: `location`, `event_starts_at`, `event_has_time` e `finalizes_at` são
  todos `NULL` (grupo antigo, R22) ou todos preenchidos.
- `ck_secret_group_finish_needs_event`: `status <> 'FINISHED' OR finalizes_at IS NOT NULL`.
- Índice parcial `ix_secret_group_finalizes (finalizes_at) WHERE status = 'DRAWN'` para a tarefa
  de finalização (R17).

### Máquina de estados

```text
                criar grupo (com data e local)
                          │
                          ▼
   ┌────────────────────────────────────────┐
   │                 OPEN                   │◀──────────────────────────┐
   │  entrar, sair, remover, confirmar,     │                           │
   │  editar detalhes (reabre confirmações) │                           │
   └────────────────────────────────────────┘                           │
          │ sortear (organizador; ≥3; todos confirmados;                │ aprovar pedido de saída
          │          data não passou; detalhes preenchidos)             │ (organizador): pares
          ▼                                                             │ apagados, quem saiu
   ┌────────────────────────────────────────┐                           │ não volta
   │                 DRAWN                  │───────────────────────────┘
   │  revelar o próprio amigo, editar       │
   │  detalhes (sem reabrir confirmações),  │
   │  pedir/cancelar/recusar saída          │
   └────────────────────────────────────────┘
          │ agora ≥ finalizes_at (tarefa agendada ou primeira escrita)
          ▼
   ┌────────────────────────────────────────┐
   │               FINISHED                 │  somente leitura; revelar o próprio amigo
   └────────────────────────────────────────┘
```

- `DRAWN → OPEN` só pela aprovação de um pedido de saída (Constituição II, FR-028, FR-031).
- `OPEN` com data vencida **não** finaliza: o sorteio fica bloqueado até nova data (US3-5).
- Não há saída de `FINISHED` (reabrir está fora do escopo).

### Métodos de domínio (`SecretGroup`)

| Método | Regra |
|--------|-------|
| `updateEventDetails(details, schedule, now)` | só `OPEN`/`DRAWN`; se data/horário/local mudaram: valida R14, recalcula R13, `details_version + 1` e devolve "confirmações devem voltar a pendente" quando `OPEN` |
| `markDrawn(now)` | `OPEN → DRAWN` (já existe); passa a zerar `draw_undone_at` |
| `undoDraw(now)` | `DRAWN → OPEN`, `drawn_at = NULL`, `draw_undone_at = now` |
| `finishIfDue(now)` | se `DRAWN` e `finalizes_at <= now`: `→ FINISHED`, `finished_at = now`; devolve se mudou |
| `drawBlockedReason(members, now)` | `event-details-required` / `event-date-passed` / `not-enough-participants` / `participants-pending` / `null` (R16) |

## 2. `group_member` — Participação (alterada)

| Campo | Tipo | Regras |
|-------|------|--------|
| `confirmed_at` | `timestamptz` NULL | `NULL` = *pendente*; preenchido = *confirmado* (FR-008) |

- O organizador nasce confirmado (`confirmed_at = joined_at`) e nunca volta a pendente.
- Quem entra pelo código nasce pendente.
- Só o próprio usuário confirma (FR-009); confirmar de novo não muda nada (FR-010).
- Alterar data, horário ou local com o grupo `OPEN` zera `confirmed_at` de todos, exceto do
  organizador (FR-015), na mesma transação.
- Depois do sorteio a confirmação não muda mais (FR-016).

## 3. `exit_request` — Pedido de saída (nova)

| Campo | Tipo | Regras |
|-------|------|--------|
| `id` | `uuid` PK | |
| `group_id` | `uuid` NOT NULL FK → `secret_group.id` | |
| `user_id` | `uuid` NOT NULL FK → `app_user.id` | quem pediu; não pode ser o organizador |
| `status` | `varchar(10)` NOT NULL | `CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))` |
| `requested_at` | `timestamptz` NOT NULL | |
| `decided_at` | `timestamptz` NULL | `CHECK ((status = 'PENDING') = (decided_at IS NULL))` |
| `decision_seen_at` | `timestamptz` NULL | quando o solicitante viu o aviso da decisão (R20); só para status ≠ `PENDING` |

- Índice único parcial `uq_exit_request_pending (group_id, user_id) WHERE status = 'PENDING'`:
  no máximo um pendente por pessoa e grupo (FR-025).
- Índice `ix_exit_request_unseen (user_id) WHERE status <> 'PENDING' AND decision_seen_at IS NULL`
  para os avisos (R20).
- FK para `secret_group` e `app_user`, **não** para `group_member`: a participação é apagada na
  aprovação, mas o pedido continua existindo para o aviso.
- Criar: grupo `DRAWN`, solicitante membro e não organizador, sem pendente.
- Cancelar: só o solicitante, só `PENDING` → `CANCELLED` (sem aviso, foi ele quem cancelou:
  `decision_seen_at = decided_at`).
- Recusar/aprovar: só o organizador, só `PENDING`.
- Cancelamento automático (outra saída aprovada ou grupo finalizado) → `CANCELLED` com aviso
  pendente (`decision_seen_at = NULL`).
- Nenhum campo guarda ou referencia pares do sorteio (FR-032).

## 4. `group_exit_ban` — Saída após sorteio (nova)

| Campo | Tipo | Regras |
|-------|------|--------|
| `group_id` | `uuid` FK → `secret_group.id` | PK composta `(group_id, user_id)` |
| `user_id` | `uuid` FK → `app_user.id` | |
| `created_at` | `timestamptz` NOT NULL | momento da aprovação |

- Gravada na mesma transação da aprovação; consultada em `join` (FR-030, R19).

## 5. `draw_assignment` — Pares 🔒 (regra de acesso nova)

Sem mudança de colunas. Nova operação permitida: `deleteByGroupId(groupId)` — `DELETE` em massa
(`@Modifying`), usada **só** por `DrawService.discardDraw`. Continua proibido qualquer método que
**leia** os pares de um grupo inteiro (Constituição I).

---

## Validações (espelhadas no frontend com zod)

| Campo | Regra | Mensagem (pt-BR) |
|-------|-------|------------------|
| Descrição | opcional, até 500 caracteres | "A descrição pode ter até 500 caracteres." |
| Data do evento | obrigatória; hoje ou futura em Brasília, quando informada ou alterada | "A data do evento precisa ser hoje ou no futuro." |
| Horário do evento | opcional, `HH:mm`; se a data é hoje, não pode ter passado | "Esse horário já passou." |
| Local do evento | obrigatório, 1–120 caracteres | "Informe o local do evento (até 120 caracteres)." |
| `detailsVersion` (confirmar) | inteiro ≥ 1 | — (erro técnico; o cliente sempre envia o que recebeu) |

## Migrações

| Versão | Conteúdo |
|--------|----------|
| `V5__event_details_and_confirmation.sql` | colunas e restrições novas de `secret_group`; `confirmed_at` em `group_member`; preenchimento de grupos antigos (R22) |
| `V6__exit_requests.sql` | `exit_request` e `group_exit_ban` com índices |
