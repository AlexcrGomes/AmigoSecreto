# Data Model: Amigo Secreto — Grupos e Sorteio Sigiloso

**Feature**: `001-amigo-secreto-sorteio` | **Data**: 2026-11-12 | **Plan**: [plan.md](./plan.md)

Banco: PostgreSQL 18. Schema criado e alterado **somente** por migrações Flyway
(`backend/src/main/resources/db/migration`). Todas as datas são `timestamptz` em UTC. Os IDs
são `uuid` gerados pela aplicação, nunca sequenciais expostos (evita enumeração).

## Diagrama

```text
app_user 1 ──< group_member >── 1 secret_group
   │                                  │
   │ 1                                │ organizer_id (FK app_user)
   └──< draw_assignment >─────────────┘
        (giver = membro do grupo; receiver cifrado)
```

---

## 1. `app_user` — Usuário

| Campo | Tipo | Regras |
|-------|------|--------|
| `id` | `uuid` PK | |
| `name` | `varchar(80)` NOT NULL | 2–80 caracteres após `trim` (FR-001) |
| `email` | `varchar(254)` NOT NULL | formato de e-mail; guardado em minúsculas e sem espaços; **único** (`uq_app_user_email`) |
| `password_hash` | `varchar(100)` NOT NULL | BCrypt via `DelegatingPasswordEncoder` (`{bcrypt}…`). A senha em claro tem de 8 a 64 caracteres e no máximo 72 bytes (FR-002, research R3) |
| `created_at` | `timestamptz` NOT NULL | |

- O hash nunca sai da camada de serviço (não aparece em DTOs nem em logs).

## 2. `secret_group` — Grupo

| Campo | Tipo | Regras |
|-------|------|--------|
| `id` | `uuid` PK | |
| `name` | `varchar(60)` NOT NULL | 1–60 caracteres após `trim` (FR-008) |
| `code` | `char(8)` NOT NULL | alfabeto `ABCDEFGHJKMNPQRSTUVWXYZ23456789`; **único** (`uq_secret_group_code`) (FR-009, R5) |
| `organizer_id` | `uuid` NOT NULL FK → `app_user.id` | o organizador também é linha em `group_member` |
| `status` | `varchar(10)` NOT NULL | `CHECK (status IN ('OPEN','DRAWN'))`; padrão `OPEN` |
| `created_at` | `timestamptz` NOT NULL | |
| `drawn_at` | `timestamptz` NULL | `CHECK ((status = 'DRAWN') = (drawn_at IS NOT NULL))` |
| `version` | `bigint` NOT NULL | lock otimista complementar |

### Máquina de estados

```text
          criar grupo
              │
              ▼
        ┌──────────┐   iniciar sorteio (organizador, ≥3 membros)   ┌──────────┐
        │   OPEN   │ ─────────────────────────────────────────────▶│  DRAWN   │
        └──────────┘                                                └──────────┘
   entrar / sair / remover: permitidos       entrar / sair / remover / sortear de novo: proibidos (409)
```

- Não há transição de `DRAWN` de volta para `OPEN` (resultado definitivo — Clarifications).
- Se o sorteio falhar, a transação inteira é desfeita e o grupo continua `OPEN` (FR-026).

## 3. `group_member` — Participação

| Campo | Tipo | Regras |
|-------|------|--------|
| `group_id` | `uuid` FK → `secret_group.id` | PK composta `(group_id, user_id)`: um usuário entra no máximo uma vez por grupo (FR-012) |
| `user_id` | `uuid` FK → `app_user.id` | |
| `joined_at` | `timestamptz` NOT NULL | ordena a lista de participantes |

- Índice `ix_group_member_user (user_id)` para listar "meus grupos" (FR-016).
- Limite de **100** membros por grupo, verificado no serviço com a linha do grupo travada.
- O organizador não pode sair nem ser removido (Edge Cases).
- Sair e remover só são permitidos com o grupo em `OPEN` (FR-018, FR-019).

## 4. `draw_assignment` — Resultado do sorteio (par) 🔒

| Campo | Tipo | Regras |
|-------|------|--------|
| `group_id` | `uuid` | PK composta `(group_id, giver_id)`: um par por participante |
| `giver_id` | `uuid` | FK composta `(group_id, giver_id)` → `group_member` |
| `receiver_enc` | `bytea` NOT NULL | `IV(12 bytes) ‖ ciphertext ‖ tag GCM` do `user_id` de quem foi tirado; AAD = `group_id ‖ giver_id` (research R9) |
| `created_at` | `timestamptz` NOT NULL | |

**Invariantes garantidas pelo domínio** (validadas antes de gravar e cobertas por testes de
propriedade, porque o valor cifrado não permite índice):

1. O número de pares é igual ao número de membros do grupo.
2. Todo membro aparece exatamente uma vez como `giver` (garantido também pela PK).
3. Todo membro aparece exatamente uma vez como `receiver`.
4. `giver ≠ receiver` para todos os pares (ninguém tira a si mesmo).

**Regras de acesso** (Constituição I):

- A única consulta de leitura é `findByGroupIdAndGiverId(groupId, idDoUsuarioAutenticado)`.
- Nenhum repositório expõe `findAll` ou `findByGroupId` desses pares.
- `toString()` da entidade não inclui o receiver; o valor decifrado nunca vai para log.

## 5. `spring_session` / `spring_session_attributes`

Tabelas padrão do Spring Session JDBC (sessões de login, research R2), criadas por migração
Flyway com o script oficial do PostgreSQL. A expiração por inatividade é de 8 horas e as
sessões vencidas são limpas por uma tarefa agendada do próprio Spring Session.

---

## Validações (espelhadas no frontend com zod)

| Campo | Regra | Mensagem (pt-BR) |
|-------|-------|------------------|
| Nome do usuário | 2–80 caracteres | "Informe seu nome (2 a 80 caracteres)." |
| E-mail | formato válido, ≤ 254 | "Informe um e-mail válido." |
| Senha | 8–64 caracteres, ≤ 72 bytes | "A senha deve ter entre 8 e 64 caracteres." |
| Nome do grupo | 1–60 caracteres | "Dê um nome ao grupo (até 60 caracteres)." |
| Código | 8 caracteres do alfabeto após normalizar (maiúsculas, sem espaços e sem hífen) | "Código inválido." |

## Migrações iniciais

| Versão | Conteúdo |
|--------|----------|
| `V1__create_users.sql` | `app_user` |
| `V2__create_spring_session.sql` | tabelas do Spring Session JDBC |
| `V3__create_groups.sql` | `secret_group`, `group_member` |
| `V4__create_draw_assignment.sql` | `draw_assignment` |
