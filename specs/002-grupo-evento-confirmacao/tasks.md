---

description: "Lista de tarefas da feature 002 — detalhes do evento, confirmação, finalização e saída após o sorteio"
---

# Tasks: Detalhes do Evento, Confirmação de Participação e Grupo Finalizado

**Input**: Design documents from `/specs/002-grupo-evento-confirmacao/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **OBRIGATÓRIOS** (Constituição IV). Em cada história, as tarefas de teste vêm
**antes** da implementação e MUST falhar antes do código existir (Red → Green → Refactor).
Backend: JUnit 5 + Mockito + AssertJ; integração com Testcontainers (PostgreSQL 18.7). Frontend:
Vitest + React Testing Library + MSW. Gate de cobertura: 80% de linhas (JaCoCo e Vitest).

**Organization**: tarefas agrupadas por história de usuário (US1–US4 da spec).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: história de usuário da tarefa (US1, US2, US3, US4)

## Path Conventions

- Backend: `backend/src/main/java/br/com/amigosecreto/…` (abreviado como `…/`) e
  `backend/src/test/java/br/com/amigosecreto/…` (abreviado como `test/…/`)
- Migrações: `backend/src/main/resources/db/migration/`
- Frontend: `frontend/src/…`; testes ao lado do código (`*.test.ts(x)`)
- Docker roda **no WSL**: build e testes como no [quickstart da 001](../001-amigo-secreto-sorteio/quickstart.md)

## Regras transversais (valem para TODAS as tarefas)

- **Sigilo (Constituição I)**: nenhum DTO, log, mensagem de erro ou evento SSE contém um par.
  `GET /groups/{groupId}/my-secret-friend` continua a única leitura de amigo secreto. Pedidos de
  saída e avisos de sorteio desfeito não dizem quem a pessoa havia tirado (FR-032).
- **Lock único**: toda operação que muda grupo, participação, confirmação ou pedido de saída trava
  a linha do grupo com `SecretGroupRepository.findByIdForUpdate` (research R7, R15) e chama
  `group.finishIfDue(clock.instant())` antes de qualquer regra (R17) a partir da US3.
- **Datas**: gravadas como `timestamptz` UTC; a API fala `eventDate` (`AAAA-MM-DD`) e `eventTime`
  (`HH:mm`) em horário de Brasília; a conversão é só do `EventSchedule` (R13).
- **Sem dependências novas** nem atualização de versões (política do projeto e plan.md).
- Mensagens em **pt-BR**; erros em `application/problem+json` com `type` `/problems/<código>`.
- Texto digitado pelo usuário (descrição, local) nunca é renderizado como HTML (FR-007).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: partir de uma base verde. Não há dependência nova nem mudança de infraestrutura.

- [X] T001 Confirmar a base: na branch `002-grupo-evento-confirmacao`, rodar `mvn -B verify` (backend) e `npm run lint && npm run format:check && npm run test:coverage` (frontend) nos containers do quickstart da 001 e registrar que tudo está verde antes de mudar código
- [X] T002 [P] Atualizar `test/…/integration/OpenApiContractIT.java` para ler `../specs/001-amigo-secreto-sorteio/contracts/openapi.yaml` **e** `../specs/002-grupo-evento-confirmacao/contracts/openapi.yaml` e montar a **união** das rotas (`MÉTODO /api/v1<path>`) (research R24). Enquanto as histórias são entregues em PRs separados, o teste exige só que **toda rota gerada pelo springdoc esteja documentada** (geradas ⊆ união), para o CI de cada PR continuar verde; a igualdade completa volta na T072

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esquema do banco, regras de data e mapeamentos que TODAS as histórias usam.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase terminar.

### Tests (escrever primeiro e ver falhar)

- [X] T003 [P] Criar `test/…/group/EventScheduleTest.java` (classe pura, sem Spring, `Clock` fixo): data sem horário → `startsAt` = meia-noite de Brasília convertida para UTC (ex.: 2026-12-20 → `2026-12-20T03:00:00Z`) e `finalizesAt` = meia-noite do dia seguinte em Brasília (`2026-12-21T03:00:00Z`); data com horário 19:00 → `startsAt = finalizesAt = 2026-12-20T22:00:00Z`; "hoje" calculado em Brasília quando são 01:30 UTC (ainda é o dia anterior em Brasília); `isPast` para data anterior a hoje; `hasHappened(finalizesAt)` verdadeiro a partir do horário do evento (ou do fim do dia, sem horário) — usado para `event-date-passed` e `eventDatePassed` (R16); `isTimePast` para hoje com horário já passado; conversão de volta de `startsAt` + `hasTime` para `eventDate`/`eventTime` (R13, R14)
- [X] T004 [P] Criar `test/…/integration/MigrationV5IT.java`: usando Flyway com `target = 4`, inserir dados da 001 (um grupo `OPEN` com organizador + 2 membros, um grupo `DRAWN` com 3 membros e pares), depois migrar até a última versão e verificar (research R22): detalhes do evento `NULL`; `details_version = 1`; organizador com `confirmed_at = joined_at`; membros do grupo `DRAWN` com `confirmed_at = drawn_at`; demais membros do grupo `OPEN` com `confirmed_at NULL`; `draw_assignment` intacta; inserir `status = 'FINISHED'` sem `finished_at` falha pela `ck_secret_group_finished`; inserir `location` sem `event_starts_at` falha pela `ck_secret_group_event`

### Implementation

- [X] T005 Criar `backend/src/main/resources/db/migration/V5__event_details_and_confirmation.sql` conforme [data-model.md §1–§2](./data-model.md): em `secret_group` adicionar `description varchar(500) NULL`, `location varchar(120) NULL`, `event_starts_at timestamptz NULL`, `event_has_time boolean NULL`, `finalizes_at timestamptz NULL`, `details_version int NOT NULL DEFAULT 1`, `finished_at timestamptz NULL`, `draw_undone_at timestamptz NULL`; trocar o `CHECK` de `status` por `status IN ('OPEN','DRAWN','FINISHED')`; recriar `ck_secret_group_drawn` como `(status IN ('DRAWN','FINISHED')) = (drawn_at IS NOT NULL)`; criar `ck_secret_group_finished` `(status = 'FINISHED') = (finished_at IS NOT NULL)`, `ck_secret_group_event` (os quatro campos `location`, `event_starts_at`, `event_has_time`, `finalizes_at` todos `NULL` ou todos preenchidos) e `ck_secret_group_finish_needs_event` (`status <> 'FINISHED' OR finalizes_at IS NOT NULL`); índice parcial `ix_secret_group_finalizes (finalizes_at) WHERE status = 'DRAWN'`; em `group_member` adicionar `confirmed_at timestamptz NULL` e preencher como em R22 (organizador = `joined_at`; membros de grupos `DRAWN` = `drawn_at` do grupo) — faz T004 passar
- [X] T006 [P] Alterar `…/group/GroupStatus.java`: adicionar `FINISHED` e atualizar o Javadoc para `OPEN → DRAWN → FINISHED`, com `DRAWN → OPEN` só por saída aprovada
- [X] T007 [P] Alterar `…/common/TimeConfig.java`: expor o bean `ZoneId eventZone()` = `America/Sao_Paulo` (constante pública `EVENT_ZONE`), mantendo o `Clock` UTC
- [X] T008 Criar `…/group/EventSchedule.java` (classe pura e final, recebe `Clock` e `ZoneId`) com: `today()`, `startsAt(LocalDate, LocalTime|null)`, `finalizesAt(LocalDate, LocalTime|null)`, `isPast(LocalDate)`, `hasHappened(Instant finalizesAt)`, `isTimePast(LocalDate, LocalTime)`, `toLocalDate(Instant)`, `toLocalTime(Instant, boolean hasTime)`; registrar como `@Component` via construtor — faz T003 passar
- [X] T009 [P] Criar `…/group/EventDetails.java` (record de valor: `description` (até 500, `strip`, vazio → `null`), `eventDate`, `eventTime` (pode ser `null`), `location` (1–120 após `strip`)) com `sameSchedule(EventDetails other)` (compara data, horário e local — a descrição não conta, FR-015)
- [X] T010 Alterar `…/group/SecretGroup.java`: mapear as colunas novas de T005 (`description`, `location`, `eventStartsAt`, `eventHasTime`, `finalizesAt`, `detailsVersion`, `finishedAt`, `drawUndoneAt`) com getters; `eventDetails(EventSchedule)` devolve `Optional<EventDetails>` (vazio em grupo antigo); `markDrawn` passa a zerar `drawUndoneAt`. Ajustar `test/…/group/SecretGroupTest.java` para os campos novos (depende de T005, T006, T008, T009)
- [X] T011 [P] Alterar `…/group/GroupMember.java`: mapear `confirmed_at` (`confirmedAt`, getter `isConfirmed()`); construtor existente cria pendente; novo fábrica `GroupMember.organizer(groupId, userId, now)` cria já confirmado (`confirmedAt = joinedAt`)
- [X] T012 Alterar `…/events/GroupEvents.java`: adicionar `GroupUpdatedEvent(UUID groupId, GroupStatus status, int participantCount, boolean confirmationsReset)`, `DrawUndoneEvent(UUID groupId, int participantCount)`, `GroupFinishedEvent(UUID groupId, int participantCount)` e o motivo `EXIT_APPROVED` em `RevocationReason`
- [X] T013 Primeiro ampliar `test/…/events/SseEmitterRegistryTest.java` e vê-lo falhar; depois alterar `…/events/SseEmitterRegistry.java` até passar: repassar, após o commit, `group-updated` (com `confirmationsReset`), `draw-undone` e `group-finished` com o `data` de [contracts/sse-events.md](./contracts/sse-events.md); `membership-revoked` passa a levar `reason` (`removed` / `exit-approved`) e continua só para o usuário afetado; `LEFT` continua sem aviso (depende de T012)
- [X] T014 [P] Atualizar `test/…/integration/ApiClient.java`: `createGroup(name)` passa a enviar `eventDate` = hoje + 30 dias e `location = "Local de teste"` (campos ignorados até a US1, obrigatórios depois), com sobrecarga `createGroup(name, Map<String, Object> eventDetails)` para os testes da US1
- [X] T015 [P] Criar `frontend/src/lib/eventDate.ts` e `frontend/src/lib/eventDate.test.ts` (teste primeiro): `todayInBrasilia(now)`, `isPastDate`, `isPastTimeToday`, `formatEventDate(date, time)` → "sábado, 20 de dezembro de 2026, às 19:00" ou sem horário, com `Intl.DateTimeFormat('pt-BR', { timeZone: 'America/Sao_Paulo' })` (research R23)
- [X] T016 [P] Alterar `frontend/src/api/groups.ts` e `frontend/src/test/groups.ts`: tipos `GroupStatus` com `FINISHED`; `EventDetails`, `GroupDetail` e `GroupSummary` com os campos novos de [contracts/openapi.yaml](./contracts/openapi.yaml) (`eventDetails`, `eventDetailsMissing`, `eventDatePassed`, `detailsVersion`, `confirmedCount`, `myConfirmation`, `drawBlockedReason`, `finishedAt`, `drawUndone`, `myExitRequest`, `pendingExitRequests`; `Participant.confirmed`; `GroupSummary.eventDate/eventTime`); atualizar as fábricas de dados de teste com valores padrão para esses campos

**Checkpoint**: banco migrado, `EventSchedule` testado, eventos e tipos prontos. `OpenApiContractIT` verde no modo "toda rota gerada está documentada".

---

## Phase 3: User Story 1 - Informar descrição, data e local do evento (Priority: P1) 🎯 MVP

**Goal**: o organizador cria o grupo com descrição, data, horário opcional e local; todos veem os detalhes; o organizador edita e a mudança aparece ao vivo.

**Independent Test**: o organizador cria um grupo com descrição, data e local; outro usuário entra pelo código e vê os mesmos detalhes; o organizador altera o local e o outro vê sem recarregar.

### Tests for User Story 1 ⚠️

- [X] T017 [P] [US1] Ampliar `test/…/group/SecretGroupTest.java`: `updateEventDetails` só em `OPEN`/`DRAWN` (em `FINISHED` lança); mudar data, horário ou local incrementa `detailsVersion` e recalcula `eventStartsAt`/`finalizesAt`; mudar só a descrição não incrementa; validação R14 só roda quando data ou horário mudam (editar o local de um grupo com data vencida é permitido); em grupo antigo, informar os detalhes preenche todos os campos de uma vez
- [X] T018 [P] [US1] Ampliar `test/…/group/GroupServiceTest.java`: `create` com detalhes grava os campos e o organizador já confirmado; data no passado → `UnprocessableException` `event-date-in-past`; hoje com horário passado → `event-time-in-past`; `updateEventDetails` por não organizador → `ForbiddenException`; em grupo `FINISHED` → `ConflictException` `group-finished`; publica `GroupUpdatedEvent`; `listMine` traz `eventDate`/`eventTime`
- [X] T019 [P] [US1] Ampliar `test/…/web/GroupControllerWebTest.java`: `POST /api/v1/groups` sem `eventDate` ou sem `location` → `400` com `errors`; `description` com 501 caracteres → `400`; `eventTime` fora de `HH:mm` → `400`; `PUT /api/v1/groups/{id}/event-details` sem CSRF → `403`; resposta de `GET /groups/{id}` traz `eventDetails` e `eventDetailsMissing`
- [X] T020 [P] [US1] Criar `test/…/integration/EventDetailsFlowIT.java`: A cria grupo com descrição multilinha, data e local `<b>casa</b>`; B entra pelo código e recebe os mesmos detalhes (texto idêntico, sem escape adicional); A altera o local e B recebe `group-updated` no SSE em ≤ 5 s; B tenta `PUT event-details` → `403`; não membro → `404`
- [X] T021 [P] [US1] Criar `frontend/src/components/group/EventDetailsForm.test.tsx`, `frontend/src/components/group/EventDetailsCard.test.tsx` e ampliar `frontend/src/pages/CreateGroupPage.test.tsx` e `frontend/src/pages/HomePage.test.tsx`: data e local obrigatórios; data de ontem e horário passado de hoje recusados com as mensagens de [data-model.md](./data-model.md); contador 0/500 da descrição; cartão mostra a data formatada, o local e a descrição com quebras de linha e `<b>` como texto; botão "Editar detalhes" só para o organizador; aviso "Informe a data e o local do evento" para organizador de grupo antigo; tela inicial mostra a data de cada grupo; botão de salvar mostra carregando e erro do servidor (FR-024)

### Implementation for User Story 1

- [X] T022 [P] [US1] Criar `…/group/dto/EventDetailsRequest.java` (Bean Validation: `description` opcional `@Size(max = 500)` com "A descrição pode ter até 500 caracteres."; `eventDate` `@NotNull`; `eventTime` opcional no formato `HH:mm`; `location` `@NotBlank @Size(max = 120)` com "Informe o local do evento (até 120 caracteres)."; `strip` no construtor compacto) e `…/group/dto/EventDetailsResponse.java` (`description`, `eventDate`, `eventTime`, `location`)
- [X] T023 [US1] Alterar `…/group/dto/CreateGroupRequest.java` para incluir os campos de `EventDetailsRequest` (mesmas validações) além de `name`; alterar `…/group/dto/GroupSummary.java` (+ `eventDate`, `eventTime`) e `…/group/dto/GroupDetail.java` (+ `eventDetails`, `eventDetailsMissing`, `detailsVersion`) e a consulta `findSummaries` de `…/group/GroupMemberRepository.java`/`…/group/GroupSummaryView.java` para trazer `event_starts_at` e `event_has_time`
- [X] T024 [US1] Implementar em `…/group/SecretGroup.java` o método `updateEventDetails(EventDetails, EventSchedule, Instant now)` devolvendo se data/horário/local mudaram, com as validações de R14 lançando exceções de domínio mapeadas para `422` (`event-date-in-past`, `event-time-in-past`) — faz T017 passar
- [X] T025 [US1] Alterar `…/group/GroupService.java`: `create` grava os detalhes (via `updateEventDetails` no novo grupo) e salva o organizador com `GroupMember.organizer`; novo `updateEventDetails(userId, groupId, EventDetailsRequest)` com lock, só organizador, publica `GroupUpdatedEvent(confirmationsReset = false)` (o reset entra na US2); `detail()` e `listMine` preenchem os campos novos — faz T018 passar
- [X] T026 [US1] Alterar `…/group/GroupController.java`: `PUT /api/v1/groups/{groupId}/event-details` com `@Valid EventDetailsRequest`, devolvendo `GroupDetail`; registrar os novos `type` de problema em `…/common/Problems.java` se necessário — faz T019 e T020 passarem
- [X] T027 [P] [US1] Alterar `frontend/src/lib/schemas.ts`: schema zod `eventDetailsSchema` (descrição ≤ 500; data obrigatória, hoje ou futura em Brasília; horário opcional `HH:mm`, não passado se a data é hoje; local 1–120) e `createGroupSchema` = nome + detalhes, usando `lib/eventDate.ts`
- [X] T028 [P] [US1] Alterar `frontend/src/api/groups.ts`: `createGroup` envia os detalhes; nova `updateEventDetails(groupId, body)` (`PUT`)
- [X] T029 [US1] Criar `frontend/src/components/group/EventDetailsForm.tsx` (react-hook-form + zod; `<input type="date">`, `<input type="time">`, `<textarea>` com contador, rótulos e mensagens de erro acessíveis, alvos ≥ 44px) e `frontend/src/components/group/EventDetailsCard.tsx` (texto do React com `whitespace-pre-line`, nunca `dangerouslySetInnerHTML`)
- [X] T030 [US1] Alterar `frontend/src/pages/CreateGroupPage.tsx` para usar `EventDetailsForm` com o nome do grupo; alterar `frontend/src/pages/GroupPage.tsx` para mostrar `EventDetailsCard`, o botão "Editar detalhes" (organizador, `OPEN`/`DRAWN`) com o formulário num `Modal`, e o aviso de grupo antigo; alterar `frontend/src/pages/HomePage.tsx` para mostrar a data do evento nos cartões — faz T021 passar

**Checkpoint**: US1 funciona sozinha — grupos nascem com detalhes, todos veem, o organizador edita ao vivo.

---

## Phase 4: User Story 2 - Confirmar participação antes do sorteio (Priority: P1) 🎯 MVP

**Goal**: quem entra fica pendente até confirmar; o sorteio só é liberado com todos confirmados, ≥ 3 participantes e data por vir; mudar data/horário/local reabre as confirmações; a confirmação vale só para a versão vista.

**Independent Test**: com 3 participantes e um pendente, o organizador não consegue sortear e vê quem falta; o pendente confirma e o sorteio é liberado.

### Tests for User Story 2 ⚠️

- [ ] T031 [P] [US2] Ampliar `test/…/group/SecretGroupTest.java`: `drawBlockedReason(totalParticipants, pendingCount, schedule)` devolve, nesta ordem, `event-details-required` (grupo antigo), `event-date-passed`, `not-enough-participants`, `participants-pending` ou `null` (research R16); `event-date-passed` vale quando agora ≥ `finalizesAt` — evento hoje às 19:00 bloqueia às 19:00 e libera às 18:59; sem horário, libera até 23:59 de Brasília
- [ ] T032 [P] [US2] Criar `test/…/group/GroupConfirmationServiceTest.java`: confirma o usuário da sessão com a versão atual; versão diferente → `ConflictException` `details-changed` com "Os detalhes do evento mudaram. Confira e confirme de novo."; já confirmado → nada muda, sem evento (idempotente, FR-010); grupo `DRAWN` → `409 group-already-drawn`; não membro → `404`; publica `GroupUpdatedEvent(confirmationsReset = false)` ao confirmar
- [ ] T033 [P] [US2] Ampliar `test/…/group/GroupServiceTest.java`: `updateEventDetails` em `OPEN` que muda data/horário/local chama `resetConfirmations(groupId, organizerId)` e publica `confirmationsReset = true`; só descrição não reseta; em `DRAWN` nunca reseta (FR-016); `join` cria participante pendente; `detail()` preenche `participants[].confirmed`, `confirmedCount`, `myConfirmation`, `drawBlockedReason`, `canDraw` (organizador e motivo nulo)
- [ ] T034 [P] [US2] Ampliar `test/…/draw/DrawServiceTest.java`: sorteio recusado com `422` e `type` correto para cada motivo de R16; `participants-pending` traz a lista `pending` com os nomes; com todos confirmados, sorteia como antes
- [ ] T035 [P] [US2] Criar `test/…/web/ConfirmationControllerWebTest.java` (ou ampliar `GroupControllerWebTest`): `POST /groups/{id}/confirmation` sem corpo ou com `detailsVersion < 1` → `400`; sem CSRF → `403`; problema `details-changed` → `409`; ampliar `test/…/web/DrawControllerWebTest.java` para o `422` com `pending`
- [ ] T036 [P] [US2] Criar `test/…/integration/ConfirmationFlowIT.java`: fluxo completo da US2 (cenários 1–10) com 3 usuários; corrida **confirmar × alterar local** com duas threads repetida 50 vezes — ao final, ou a pessoa está pendente, ou confirmou a versão nova (nunca confirmada com versão antiga) (FR-035); corrida **entrar × sortear** com todos confirmados — o sorteio nunca acontece com um pendente (SC-003); confirmação repetida em paralelo → um único `confirmed_at`
- [ ] T037 [P] [US2] Criar `frontend/src/components/group/ConfirmParticipationCard.test.tsx` e ampliar `frontend/src/components/group/DrawButton.test.tsx` e `frontend/src/pages/GroupPage.open.test.tsx`: pendente vê o cartão com os detalhes e "Confirmar participação"; resposta `details-changed` recarrega o grupo e mostra "Os detalhes mudaram. Confira e confirme de novo."; lista com selos Confirmado/Pendente e "N de M confirmados"; botão de sortear desabilitado com o texto de cada `drawBlockedReason` ("Falta(m) N confirmar: Ana, Beto", etc.); sinal `group-updated` com `confirmationsReset` mostra o aviso para quem não é organizador; organizador vê o aviso "Todos precisarão confirmar de novo" ao editar data/local com o grupo aberto; botão "Confirmar participação" mostra carregando e erro (FR-024)

### Implementation for User Story 2

- [ ] T038 [P] [US2] Alterar `…/group/GroupMemberRepository.java`: `resetConfirmations(groupId, organizerId)` (`@Modifying` `UPDATE group_member SET confirmed_at = NULL WHERE group_id = :groupId AND user_id <> :organizerId`), `countByIdGroupIdAndConfirmedAtIsNull`, e a projeção de participantes (`ParticipantView`) com `confirmedAt`; `findPendingNames(groupId)` ordenado por `joined_at`
- [ ] T039 [P] [US2] Criar `…/group/dto/ConfirmationRequest.java` (`@NotNull @Min(1) Integer detailsVersion`) e alterar `…/group/dto/Participant.java` (+ `confirmed`) e `…/group/dto/GroupDetail.java` (+ `confirmedCount`, `myConfirmation`, `drawBlockedReason`)
- [ ] T040 [US2] Implementar `drawBlockedReason` em `…/group/SecretGroup.java` e `confirm(Instant)` em `…/group/GroupMember.java` (idempotente) — faz T031 passar
- [ ] T041 [US2] Criar `…/group/GroupConfirmationService.java` (`@Transactional`, lock do grupo, versão, idempotência, evento após o commit) — faz T032 passar
- [ ] T042 [US2] Alterar `…/group/GroupService.java`: reset das confirmações em `updateEventDetails` quando `OPEN` e o agendamento mudou; `detail()` com os campos de confirmação e `canDraw` derivado de `drawBlockedReason` — faz T033 passar
- [ ] T043 [US2] Alterar `…/draw/DrawService.java`: dentro do lock, antes do derangement, aplicar `drawBlockedReason` e lançar `UnprocessableException` com o `type` de R16; para `participants-pending`, incluir `pending` (nomes) no Problem — estender `…/common/UnprocessableException.java`/`…/common/GlobalExceptionHandler.java` para propriedades extras, sem nunca incluir pares — faz T034 passar
- [ ] T044 [US2] Alterar `…/group/GroupController.java`: `POST /api/v1/groups/{groupId}/confirmation` com `@Valid ConfirmationRequest`, devolvendo `GroupDetail` (usuário sempre da sessão); em `test/…/integration/ApiClient.java`, novo helper `confirm(groupId)` que lê `detailsVersion` do `GET /groups/{id}` e envia a confirmação, usado em `GroupFlowIT`, `MembershipFlowIT`, `DrawConcurrencyIT`, `DrawPerformanceIT` e `SecrecyIT` para confirmar os participantes antes de sortear (sem isso eles passam a receber `422 participants-pending`) — faz T035 e T036 passarem
- [ ] T045 [P] [US2] Alterar `frontend/src/api/groups.ts` (`confirmParticipation(groupId, detailsVersion)`) e `frontend/src/lib/errors.ts` (mensagem de `details-changed` e textos dos motivos de bloqueio)
- [ ] T046 [US2] Criar `frontend/src/components/group/ConfirmParticipationCard.tsx`; alterar `frontend/src/components/group/ParticipantList.tsx` (selos e contador), `frontend/src/components/group/DrawButton.tsx` (motivo do bloqueio a partir de `drawBlockedReason`), `frontend/src/hooks/useGroupEvents.ts` (`group-updated` invalida a consulta e, com `confirmationsReset`, dispara o aviso) e `frontend/src/pages/GroupPage.tsx` (cartão do pendente, "Não vou participar" usando o sair existente, aviso ao editar) — faz T037 passar

**Checkpoint**: MVP completo (US1 + US2), com o CI verde.

---

## Phase 5: User Story 3 - Grupo finalizado depois do evento (Priority: P2)

**Goal**: o grupo sorteado vira *finalizado* sozinho no horário do evento (ou no fim do dia), fica somente leitura, aparece em seção própria e cada um ainda revela o próprio amigo.

**Independent Test**: um grupo sorteado cujo evento já aconteceu aparece como finalizado em seção separada; o participante revela o próprio amigo e não encontra nada para alterar.

### Tests for User Story 3 ⚠️

- [ ] T047 [P] [US3] Ampliar `test/…/group/SecretGroupTest.java`: `finishIfDue(now)` muda `DRAWN → FINISHED` quando `finalizesAt <= now` e grava `finishedAt`; não faz nada em `OPEN` (mesmo com data vencida — US3-5), em `FINISHED` ou antes da hora; devolve se mudou
- [ ] T048 [P] [US3] Criar `test/…/group/GroupFinalizerTest.java` (Mockito, `Clock` fixo): busca os vencidos, finaliza cada um numa transação própria, publica `GroupFinishedEvent` por grupo e segue para o próximo se um falhar
- [ ] T049 [P] [US3] Ampliar `test/…/group/GroupServiceTest.java`, `test/…/group/GroupMembershipServiceTest.java`, `test/…/group/GroupConfirmationServiceTest.java` e `test/…/draw/SecretFriendServiceTest.java`: em grupo `FINISHED`, `join`, `leave`, `remove`, `confirm`, `updateEventDetails` e `draw` → `409 group-finished` ("Este grupo foi finalizado."); um grupo `DRAWN` vencido é finalizado pela primeira escrita (`finishIfDue`) e a escrita é recusada; `my-secret-friend` funciona em `FINISHED`; `detail()` traz `eventDatePassed = true` só para `OPEN` com data vencida, e `finishedAt`
- [ ] T050 [P] [US3] Criar `test/…/integration/FinalizationIT.java` (a API não aceita datas passadas, então o teste move `event_starts_at`/`finalizes_at` para o passado com `JdbcTemplate`, como no quickstart §3, e chama `GroupFinalizer` diretamente em vez de esperar o agendamento): grupo sorteado com `finalizes_at` no passado é finalizado pela tarefa e os membros conectados recebem `group-finished`; grupo `OPEN` vencido continua `OPEN` e o sorteio responde `422 event-date-passed`; organizador atualiza a data e consegue sortear; grupo finalizado responde `409` às escritas pela API e `200` a `my-secret-friend` só com o próprio par
- [ ] T051 [P] [US3] Ampliar `frontend/src/pages/HomePage.test.tsx` e criar `frontend/src/pages/GroupPage.finished.test.tsx`: seções "Em andamento" e "Finalizados" com selo "Finalizado"; grupo finalizado sem nenhum botão de alteração e com "Revelar"; sinal `group-finished` muda a tela para somente leitura com o aviso; organizador de grupo `OPEN` com `eventDatePassed` vê "A data do evento já passou. Atualize a data para sortear."

### Implementation for User Story 3

- [ ] T052 [US3] Implementar `finishIfDue(Instant)` em `…/group/SecretGroup.java` — faz T047 passar
- [ ] T053 [US3] Alterar `…/group/SecretGroupRepository.java`: `findIdsDueForFinish(Instant now, int limit)` com consulta nativa `SELECT id FROM secret_group WHERE status = 'DRAWN' AND finalizes_at <= :now ORDER BY finalizes_at LIMIT :limit FOR UPDATE SKIP LOCKED`
- [ ] T054 [US3] Criar `…/group/GroupFinalizer.java`: `@Scheduled(fixedDelay = 60_000, initialDelay = 0)`, lotes de 100, cada grupo numa transação (`TransactionTemplate`) com lock, `finishIfDue`, evento `GroupFinishedEvent` após o commit; log só com o `groupId` — faz T048 passar
- [ ] T055 [US3] Aplicar a regra transversal: chamar `group.finishIfDue(clock.instant())` logo após o lock e recusar `FINISHED` com `ConflictException("group-finished", "Este grupo foi finalizado.")` em `…/group/GroupService.java` (`join`, `updateEventDetails`), `…/group/GroupMembershipService.java` (`leave`, `remove`), `…/group/GroupConfirmationService.java` e `…/draw/DrawService.java`; publicar `GroupFinishedEvent` quando `finishIfDue` mudar o estado; `…/draw/SecretFriendService.java` aceita `DRAWN` e `FINISHED`; `detail()` com `eventDatePassed` e `finishedAt` — faz T049 e T050 passarem
- [ ] T056 [US3] Alterar `frontend/src/hooks/useGroupEvents.ts` (`group-finished` invalida grupo e lista), `frontend/src/pages/HomePage.tsx` (duas seções, selo "Finalizado"), `frontend/src/pages/GroupPage.tsx` (modo somente leitura em `FINISHED`; aviso de data vencida para o organizador) — faz T051 passar

**Checkpoint**: US3 funciona com US1 + US2; grupos vencidos finalizam sozinhos.

---

## Phase 6: User Story 4 - Sair de um grupo já sorteado, com aprovação do organizador (Priority: P2)

**Goal**: depois do sorteio, o participante pede para sair; o organizador recusa (nada muda) ou aprova (a pessoa sai sem poder voltar, todos os pares são apagados, o grupo volta a aguardar sorteio); quem pediu vê a decisão mesmo se estava fora.

**Independent Test**: em um grupo sorteado com 4 participantes, um pede para sair; o organizador aprova; ninguém vê mais o amigo antigo; o organizador sorteia de novo e os 3 que ficaram recebem novos amigos só entre eles.

### Tests for User Story 4 ⚠️

- [ ] T057 [P] [US4] Criar `test/…/integration/MigrationV6IT.java`: tabelas `exit_request` e `group_exit_ban` existem; o índice único parcial impede dois `PENDING` para o mesmo `(group_id, user_id)` mas permite um `PENDING` depois de um `REJECTED`; `ck` de `decided_at` vs `status` funciona
- [ ] T058 [P] [US4] Ampliar `test/…/group/SecretGroupTest.java`: `undoDraw(now)` só em `DRAWN`, volta a `OPEN`, zera `drawnAt` e grava `drawUndoneAt`; `markDrawn` depois disso zera `drawUndoneAt`
- [ ] T059 [P] [US4] Criar `test/…/exitrequest/ExitRequestServiceTest.java`: pedir (só `DRAWN`; organizador → `409 organizer-cannot-leave`; segundo pendente → `409 exit-request-already-pending`; antes do sorteio → `409 group-not-drawn`; finalizado → `409 group-finished`); cancelar (só o próprio pendente; `decision_seen_at = decided_at`); recusar (só organizador, só `PENDING`, `409 exit-request-not-pending` se já decidido); pedir de novo após recusa é permitido; pedir, cancelar e recusar publicam `GroupUpdatedEvent(confirmationsReset = false)` para o organizador ver o pedido e o solicitante ver a recusa ao vivo (R21, FR-026); **aprovar** na ordem de R18: `finishIfDue`, `DrawService.discardDraw`, apaga a participação, grava `GroupExitBan`, pedido `APPROVED`, demais pendentes `CANCELLED` com aviso pendente, eventos `MembershipRevokedEvent(EXIT_APPROVED)` antes de `DrawUndoneEvent` e `ParticipantsChangedEvent`; `cancelPendingOnFinish(groupId)` cancela todos com aviso
- [ ] T060 [P] [US4] Criar `test/…/exitrequest/ExitRequestNoticeServiceTest.java`: lista só pedidos do usuário da sessão com status ≠ `PENDING` e `decision_seen_at` nulo, com `groupName` mesmo para quem já saiu; `ack` marca visto, é idempotente e dá `404` para pedido de outra pessoa
- [ ] T061 [P] [US4] Ampliar `test/…/draw/DrawServiceTest.java` (`discardDraw` chama `deleteByGroupId` e `undoDraw`, nunca lê pares) e `test/…/group/GroupServiceTest.java` (`join` de quem está em `group_exit_ban` → `409 rejoin-not-allowed` com "Você saiu deste grupo depois do sorteio e não pode entrar de novo."; `detail()` traz `myExitRequest`, `pendingExitRequests` só para o organizador e `drawUndone`)
- [ ] T062 [P] [US4] Criar `test/…/web/ExitRequestControllerWebTest.java`: as seis rotas de [contracts/openapi.yaml](./contracts/openapi.yaml) (pedir, cancelar, aprovar, recusar, avisos, ack) exigem sessão e CSRF nas escritas; `403` para não organizador em aprovar/recusar; `404` para pedido de outro grupo; respostas sem nenhum campo de par
- [ ] T063 [P] [US4] Criar `test/…/integration/ExitRequestFlowIT.java`: fluxo completo da US4 com 4 usuários (cenários 1–8); **sigilo após desfazer** (SC-008): depois da aprovação, `select count(*) from draw_assignment where group_id = ?` = 0, `my-secret-friend` → `409 group-not-drawn` para todos, o novo sorteio tem exatamente os 3 que ficaram; corrida **aprovar × sortear** em paralelo (50 repetições) — nunca sobra par de quem saiu nem sorteio com 4; dois pedidos pendentes, aprovar um cancela o outro; pedido pendente continua pendente quando o organizador altera os detalhes (Edge Case); quem saiu não entra de novo; aviso guardado aparece em `GET /exit-request-notices` para quem estava sem SSE; ampliar `test/…/integration/SecrecyIT.java` para que nenhuma resposta das rotas novas contenha o nome tirado
- [ ] T064 [P] [US4] Criar `frontend/src/components/group/ExitRequestPanel.test.tsx`, `frontend/src/components/group/ExitRequestsList.test.tsx`, `frontend/src/hooks/useExitRequestNotices.test.ts` e ampliar `frontend/src/components/group/SecretFriendCard.test.tsx`, `frontend/src/pages/GroupPage.drawn.test.tsx` e `frontend/src/pages/HomePage.test.tsx`: pedir (modal explicando que o sorteio será refeito), cancelar, aviso de recusado/cancelado com "Entendi" e o texto de [ui-routes.md](./contracts/ui-routes.md) conforme a situação do grupo (cancelado com o grupo aguardando sorteio → "Agora você pode sair do grupo diretamente."; finalizado → "o grupo foi finalizado"); `group-updated` recarrega também os avisos (recusa aparece ao vivo); organizador aprova/recusa com modal ("Fulano sairá do grupo, o sorteio será desfeito para todos…"); `draw-undone` apaga o nome revelado e o cache do amigo secreto e mostra o aviso; `drawUndone` mostra o aviso fixo a quem chega depois; `membership-revoked` com `exit-approved` volta para `/` com a mensagem; tela inicial mostra "Você saiu do grupo X" e marca como visto

### Implementation for User Story 4

- [ ] T065 [US4] Criar `backend/src/main/resources/db/migration/V6__exit_requests.sql` conforme [data-model.md §3–§4](./data-model.md): `exit_request` (`id uuid PK`, `group_id` FK `secret_group`, `user_id` FK `app_user`, `status varchar(10) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))`, `requested_at timestamptz NOT NULL`, `decided_at timestamptz NULL` com `CHECK ((status = 'PENDING') = (decided_at IS NULL))`, `decision_seen_at timestamptz NULL`), `uq_exit_request_pending (group_id, user_id) WHERE status = 'PENDING'`, `ix_exit_request_unseen (user_id) WHERE status <> 'PENDING' AND decision_seen_at IS NULL`; `group_exit_ban` (`group_id` FK, `user_id` FK, `created_at timestamptz NOT NULL`, PK `(group_id, user_id)`) — faz T057 passar
- [ ] T066 [P] [US4] Criar `…/exitrequest/ExitRequestStatus.java`, `…/exitrequest/ExitRequest.java` (entidade com `approve/reject/cancel(now, notify)` e `toString` sem dados sensíveis), `…/exitrequest/ExitRequestRepository.java` (`findByGroupIdAndUserIdAndStatus`, `findByGroupIdAndStatus`, avisos não vistos por usuário com o nome do grupo, `cancelAllPending(groupId, now)` `@Modifying`), `…/exitrequest/GroupExitBan.java` e `…/exitrequest/GroupExitBanRepository.java`
- [ ] T067 [US4] Implementar `undoDraw(Instant)` em `…/group/SecretGroup.java`; adicionar `deleteByGroupId(UUID)` `@Modifying` em `…/draw/DrawAssignmentRepository.java` (sem nenhum método de leitura por grupo) e `discardDraw(SecretGroup, Instant)` em `…/draw/DrawService.java` (chamado com o lock já obtido; nunca loga pares) — faz T058 e a parte de `DrawServiceTest` de T061 passarem
- [ ] T068 [US4] Criar `…/exitrequest/ExitRequestService.java` (pedir, cancelar, recusar, aprovar, `cancelPendingOnFinish`) seguindo R18 e as regras transversais; eventos publicados na ordem da T059 — faz T059 passar
- [ ] T069 [US4] Criar `…/exitrequest/ExitRequestNoticeService.java` — faz T060 passar
- [ ] T070 [US4] Alterar `…/group/GroupService.java`: `join` consulta `GroupExitBanRepository` antes de inserir (`409 rejoin-not-allowed`); `detail()` com `myExitRequest`, `pendingExitRequests` (só organizador, com nome e data) e `drawUndone` (`OPEN` e `drawUndoneAt` não nulo) — faz o restante de T061 passar
- [ ] T071 [US4] Ligar a finalização aos pedidos (FR-033): `…/group/GroupFinalizer.java` e o caminho `finishIfDue` das escritas chamam `ExitRequestService.cancelPendingOnFinish` na mesma transação; ampliar `test/…/group/GroupFinalizerTest.java` e `test/…/integration/FinalizationIT.java` para cobrir o cancelamento com aviso (depende de T054, T068)
- [ ] T072 [US4] Criar `…/exitrequest/dto/` (`ExitRequestResponse`: `id`, `userId`, `name`, `requestedAt`; `ExitRequestNotice`: `id`, `groupId`, `groupName`, `status`, `decidedAt`) e `…/exitrequest/ExitRequestController.java` com as rotas `POST /api/v1/groups/{groupId}/exit-requests`, `DELETE /api/v1/groups/{groupId}/exit-requests/mine`, `POST /api/v1/groups/{groupId}/exit-requests/{requestId}/approve`, `POST /api/v1/groups/{groupId}/exit-requests/{requestId}/reject`, `GET /api/v1/exit-request-notices`, `POST /api/v1/exit-request-notices/{requestId}/ack`; com todas as rotas existindo, voltar `test/…/integration/OpenApiContractIT.java` à igualdade completa (geradas = união dos contratos 001 + 002, trocando as duas verificações parciais por `isEqualTo`) — faz T062, T063 e `OpenApiContractIT` passarem
- [ ] T073 [P] [US4] Alterar `frontend/src/api/groups.ts` (ou criar `frontend/src/api/exitRequests.ts`) com as seis chamadas e criar `frontend/src/hooks/useExitRequestNotices.ts` (TanStack Query; `ack` invalida a lista)
- [ ] T074 [US4] Criar `frontend/src/components/group/ExitRequestPanel.tsx` e `frontend/src/components/group/ExitRequestsList.tsx` (com `ConfirmModal`); alterar `frontend/src/components/group/SecretFriendCard.tsx` (limpar nome e cache em `draw-undone`), `frontend/src/hooks/useGroupEvents.ts` (`draw-undone`; `membership-revoked` com `reason`; `group-updated` invalida também os avisos de pedido de saída), `frontend/src/pages/GroupPage.tsx` (painéis por papel, aviso de sorteio desfeito, aviso de recusa) e `frontend/src/pages/HomePage.tsx` (avisos de saída aprovada) — faz T064 passar

**Checkpoint**: todas as histórias funcionam juntas; `OpenApiContractIT` verde na igualdade completa.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T075 [P] Atualizar `README.md`: trocar "o sorteio acontece uma única vez" pela regra da constituição v1.2.0 (sorteio definitivo, exceto saída aprovada pelo organizador) e descrever data/local, confirmação e grupos finalizados
- [ ] T076 [P] Adicionar em `specs/001-amigo-secreto-sorteio/spec.md`, junto a FR-019 e FR-025, a nota "Alterado pela feature 002: saída após o sorteio com aprovação do organizador desfaz o sorteio (constituição v1.2.0)."
- [ ] T077 [P] Revisar as anotações springdoc/Javadoc dos controllers e DTOs novos para que o `/v3/api-docs` do perfil `dev` descreva os campos como em [contracts/openapi.yaml](./contracts/openapi.yaml)
- [ ] T078 Rodar `mvn -B verify` e `npm run lint && npm run format:check && npm run test:coverage && npm run build` nos containers; corrigir até ficar verde com cobertura ≥ 80% nos dois
- [ ] T079 Executar o roteiro manual do [quickstart.md](./quickstart.md) (§1 migração com dados da 001, §2 passos 1–20 com uma janela a 360px, §3 simulação de tempo) e a verificação de sigilo (§4); registrar o resultado no PR
- [ ] T080 Revisão final contra a constituição (Princípios I a VIII) com a checklist do PR template, conferindo em especial: nenhuma rota/evento novo com pares; `draw_assignment` sem leitura por grupo; todas as escritas com lock e `finishIfDue`; nenhuma dependência nova no `pom.xml` e no `package.json`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup; **bloqueia todas as histórias**.
- **US1 (Phase 3)**: depende da Foundational.
- **US2 (Phase 4)**: depende da US1 (versão dos detalhes, reset ao editar, condições do sorteio usam os detalhes).
- **US3 (Phase 5)**: depende da US1 (`finalizes_at`); usa o sorteio com confirmações da US2 nos testes de integração.
- **US4 (Phase 6)**: depende da US2 (o novo sorteio exige todos confirmados). A T071 depende também da US3 (`GroupFinalizer`).
- **Polish (Phase 7)**: depende de todas as histórias.

```text
Setup ─▶ Foundational ─▶ US1 ─▶ US2 ─┬─▶ US3 ─────────┐
                                     └─▶ US4 (T071 ◀─ US3) ─┴─▶ Polish
```

### Within Each User Story

- Testes primeiro, vendo-os **falhar**.
- Migração → entidades e repositórios → serviços → controllers → frontend (api → hooks → componentes → páginas).
- Commit ao fim de cada tarefa ou grupo lógico (Conventional Commits).

### Parallel Opportunities

- Setup: T002 em paralelo com T001.
- Foundational: T003 e T004 (testes) em paralelo; depois T006, T007, T009, T011, T014, T015 e T016 em paralelo; T005 → T010; T012 → T013.
- Em cada história, todas as tarefas de teste [P] podem ser escritas em paralelo; o frontend da história começa assim que o contrato da história estiver estável.
- US3 e US4 podem andar em paralelo depois da US2, exceto a T071.

---

## Parallel Example: User Story 4

```bash
# Testes primeiro, em paralelo:
Task: "T057 MigrationV6IT"
Task: "T058 SecretGroupTest (undoDraw)"
Task: "T059 ExitRequestServiceTest"
Task: "T060 ExitRequestNoticeServiceTest"
Task: "T063 ExitRequestFlowIT (sigilo após desfazer, corridas)"
Task: "T064 testes do frontend da US4"

# Depois, peças independentes em paralelo:
Task: "T066 entidades e repositórios de exitrequest"
Task: "T073 API e hook de avisos no frontend"
```

---

## Implementation Strategy

### MVP

O pedido central é "todos concordam antes do sorteio": **MVP = Setup + Foundational + US1 +
US2** (as duas histórias P1). US3 e US4 (P2) são incrementos.

1. Setup → Foundational → migração validada com dados da 001.
2. US1 → **parar e validar** (criar, ver e editar detalhes com 2 dispositivos).
3. US2 → **parar e validar** (confirmação, bloqueio do sorteio, reset ao mudar o local) → **MVP pronto**.
4. US3 → validar (finalização com o tempo simulado).
5. US4 → validar (sigilo após desfazer) → Polish → PR para a `main`.

### Sugestão de entregas (PRs menores)

| PR | Conteúdo |
|----|----------|
| 1 | Phase 1 + Phase 2 + US1 |
| 2 | US2 |
| 3 | US3 |
| 4 | US4 + Polish |

Cada PR passa pelo CI, pelas varreduras e pela aprovação manual (`aprovacao-manual`).

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- [USn] mapeia a tarefa para a história da [spec.md](./spec.md).
- Todo teste deve falhar antes da implementação correspondente.
- Evitar: tarefas vagas, dois agentes no mesmo arquivo ao mesmo tempo e qualquer atalho que exponha pares.
