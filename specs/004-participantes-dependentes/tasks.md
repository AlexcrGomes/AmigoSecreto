---

description: "Lista de tarefas da feature 004 — participantes dependentes"
---

# Tasks: Participantes Dependentes

**Input**: Design documents from `/specs/004-participantes-dependentes/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **OBRIGATÓRIOS** (Constituição IV). Em cada história, as tarefas de teste vêm
**antes** da implementação e MUST falhar antes do código existir (Red → Green → Refactor).
Backend: JUnit 5 + Mockito + AssertJ; integração com Testcontainers (PostgreSQL 18.7). Frontend:
Vitest + React Testing Library + MSW. Gate de cobertura: 80% de linhas (JaCoCo e Vitest).

**Organization**: tarefas agrupadas por história de usuário (US1–US3 da spec), com uma exceção
deliberada: a **confirmação espelhada** (FR-013) e as **restrições do sorteio** (FR-009a/b) entram
na US1. Assim que um dependente pode ser adicionado (PR da US1 na `main`), ele já precisa
confirmar junto com o responsável (senão o grupo fica travado em `participants-pending`) e o
sorteio já precisa respeitar a Constituição II. Pelo mesmo motivo, a saída do responsável levando
os dependentes (FR-016), em todos os caminhos, também é da US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: história de usuário da tarefa (US1, US2, US3)

## Path Conventions

- Backend: `backend/src/main/java/br/com/amigosecreto/…` (abreviado como `…/`) e
  `backend/src/test/java/br/com/amigosecreto/…` (abreviado como `test/…/`)
- Migrações: `backend/src/main/resources/db/migration/`
- Frontend: `frontend/src/…`; testes ao lado do código (`*.test.ts(x)`)
- Docker roda **no WSL**: build e testes como no [quickstart da 001](../001-amigo-secreto-sorteio/quickstart.md)

## Regras transversais (valem para TODAS as tarefas)

- **Sigilo (Constituição I)**: o resultado de um dependente só sai em
  `GET /groups/{groupId}/my-dependents/secret-friends`, que **não recebe id** e monta o conjunto a
  partir de `group_member.guardian_id = usuário da sessão` (research R38). Nenhuma resposta traz o
  id de quem foi tirado; nenhum evento SSE, log ou mensagem de erro contém pares; o organizador não
  ganha nenhum caminho novo.
- **Agir pelo dependente**: toda rota `my-dependents/{dependentId}` busca o dependente por
  `(dependentId, groupId, guardian_id = usuário da sessão)` no `DependentLookup`; qualquer outro
  caso responde `404 not-found` igual, sem dizer se o dependente existe (R34).
- **Dependente nunca autentica**: sem e-mail e sem senha (`CHECK` no banco); nenhuma consulta de
  login, sessão ou "meus grupos" pode devolver um `app_user` do tipo `DEPENDENT`.
- **Lock único**: toda escrita trava o grupo pelo `GroupLock` (finaliza se o evento passou) e usa
  `@Transactional(noRollbackFor = GroupFinishedException.class)`, como nas features 002/003.
- **Sorteio uniforme**: só rejeição de permutações inteiras; nunca corrigir um par inválido
  "trocando" pares (R35).
- **Sem dependências novas** nem atualização de versões (política do projeto e plan.md).
- Mensagens em **pt-BR**; erros em `application/problem+json` com `type` `/problems/<código>`.
- Nome do dependente é texto simples: nunca renderizado como HTML.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: partir de uma base verde. Não há dependência nova nem mudança de infraestrutura.

- [X] T001 Confirmar a base: na branch `004-participantes-dependentes`, rodar `mvn -B verify` (backend) e `npm run lint && npm run format:check && npm run test:coverage` (frontend) nos containers e registrar que tudo está verde antes de mudar código
- [X] T002 [P] Atualizar `test/…/integration/OpenApiContractIT.java` para ler também `../specs/004-participantes-dependentes/contracts/openapi.yaml` e montar a união 001 + 002 + 003 + 004. Enquanto as histórias são entregues em PRs separados, o teste exige só que **toda rota gerada esteja documentada** (geradas ⊆ união) e que todas as rotas de 001 + 002 + 003 existam; a igualdade completa volta na T052

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esquema do banco, entidades e tipos que TODAS as histórias usam.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase terminar.

### Tests (escrever primeiro e ver falhar)

- [X] T003 [P] Criar `test/…/integration/MigrationV8IT.java` (estende `AbstractIntegrationTest`; cria contas, grupo e um pedido de saída antigo pela API antes de conferir): contas existentes ficam `kind = 'ACCOUNT'`; inserir `app_user` `DEPENDENT` com e-mail ou senha falha, e `ACCOUNT` sem e-mail ou senha falha (CHECK de [data-model.md](./data-model.md) §1); dois dependentes com `email NULL` não conflitam em `uq_app_user_email`; `group_member.guardian_id` apontando para quem não participa do grupo falha pela FK composta; `guardian_id = user_id` falha; apagar a participação do responsável apaga a dos dependentes e, em cadeia, os `wish_item` deles; pedidos antigos têm `requested_by = user_id`; `requested_by` é `NOT NULL`
- [X] T004 [P] Ampliar `test/…/auth/AppUserTest.java`: `AppUser.dependent(" Lucas ", now)` → `kind = DEPENDENT`, nome sem espaços nas pontas, e-mail e senha nulos, `isDependent() = true`; o construtor de conta continua exigindo e-mail e senha; `toString` não expõe nada além de id e nome
- [X] T005 [P] Ampliar `test/…/auth/AuthServiceTest.java`: login com e-mail vazio ou com o nome de um dependente nunca autentica e responde como credencial inválida (mesmo tempo constante do caminho atual)

### Implementation

- [X] T006 Criar `backend/src/main/resources/db/migration/V8__dependents.sql` conforme [data-model.md](./data-model.md): `app_user.kind varchar(10) NOT NULL DEFAULT 'ACCOUNT'` com `CHECK (kind IN ('ACCOUNT', 'DEPENDENT'))`; `email` e `password_hash` `DROP NOT NULL`; `CHECK ((kind = 'ACCOUNT' AND email IS NOT NULL AND password_hash IS NOT NULL) OR (kind = 'DEPENDENT' AND email IS NULL AND password_hash IS NULL))`; `group_member.guardian_id uuid NULL` com `FOREIGN KEY (group_id, guardian_id) REFERENCES group_member (group_id, user_id) ON DELETE CASCADE`, `CHECK (guardian_id IS NULL OR guardian_id <> user_id)` e índice `ix_group_member_guardian (group_id, guardian_id) WHERE guardian_id IS NOT NULL`; `exit_request.requested_by uuid NULL REFERENCES app_user (id)` → `UPDATE exit_request SET requested_by = user_id` → `SET NOT NULL`; recriar `ix_exit_request_unseen` sobre `(requested_by) WHERE status <> 'PENDING' AND decision_seen_at IS NULL`; criar `ix_exit_request_approved_by (group_id, requested_by) WHERE status = 'APPROVED'` — faz T003 passar
- [X] T007 [P] Alterar `…/auth/AppUser.java`: enum `AppUserKind { ACCOUNT, DEPENDENT }` mapeado em `kind`; `email`/`passwordHash` anuláveis só para dependente; fábrica `static AppUser dependent(String name, Instant createdAt)`; `isDependent()`; `rename(String name)` (só dependente) — faz T004 passar; conferir que `AuthService` não quebra com `email` nulo (T005)
- [X] T008 [P] Alterar `…/group/GroupMember.java`: mapear `guardian_id` (`getGuardianId()`, `isDependent()`); fábrica `GroupMember.dependentOf(GroupMember guardian, UUID dependentId, Instant now)` que copia o `confirmedAt` do responsável (R36); `mirrorConfirmation(Instant confirmedAt)`; ajustar `test/…/group/SecretGroupTest.java` (seção de `GroupMember`)
- [X] T009 [P] Alterar `…/exitrequest/ExitRequest.java`: mapear `requested_by` (`getRequestedBy()`); construtor existente passa a gravar `requestedBy = userId`; novo construtor com `requestedBy` explícito
- [X] T010 Alterar `…/group/GroupMemberRepository.java`: `findDependents(groupId, guardianId)` (ordem de `joined_at`), `countDependents(groupId, guardianId)`, `findDependent(dependentId, groupId, guardianId)` e a projeção `ParticipantView` com `guardianId` e o nome do responsável (`left join` na participação e no `app_user` do responsável); **nenhum** método que liste dependentes de todos os responsáveis com dados além do que a lista de participantes já mostra
- [X] T011 [P] Alterar `frontend/src/api/groups.ts` e `frontend/src/test/groups.ts`: tipos `Participant.dependent`/`guardianName`, `MyDependent { id; name; confirmed; wishlist: MyWishlist; exitRequest: ExitRequest | null }`, `GroupDetail.myDependents`/`maxDependents`/`canAddDependents`/`dependentsBlockedReason`, `DependentSecretFriend { dependentId; dependentName; secretFriend: SecretFriend }`, `ExitRequest.guardianName`, `ExitRequestNotice.subjectName`, conforme [contracts/openapi.yaml](./contracts/openapi.yaml); fábricas de teste com `myDependents: []`, `canAddDependents: true`, `dependentsBlockedReason: null`

**Checkpoint**: banco migrado, entidades mapeadas (`ddl-auto: validate` verde), login intacto, tipos do frontend prontos.

---

## Phase 3: User Story 1 - Adicionar um dependente ao grupo (Priority: P1) 🎯 MVP

**Goal**: o participante adiciona, renomeia e remove dependentes (só o nome) antes do sorteio; todos veem "Lucas (dependente de Ana)"; o dependente conta como participante, confirma junto com o responsável, sai junto com ele e é sorteado com as restrições da Constituição II.

**Independent Test**: Ana entra num grupo, adiciona "Lucas"; todos veem "Lucas (dependente de Ana)"; a contagem sobe; Ana renomeia e depois remove o Lucas, e ele some da lista. Num grupo Ana + Lucas + Bruno, o organizador vê "não há sorteio possível"; com mais participantes, o sorteio nunca faz Ana e Lucas se tirarem.

### Tests for User Story 1 ⚠️

- [X] T012 [P] [US1] Ampliar `test/…/draw/DerangementGeneratorTest.java` (R35, R42): `generate(participants, forbidden)` nunca devolve par proibido nem auto-sorteio e é sempre bijeção (propriedade com grupos aleatórios de 3 a 100, responsáveis com 0–5 dependentes); `isFeasible` bate com força bruta para **todas** as configurações de responsáveis/dependentes com n ≤ 7; grupo inviável (Ana, Lucas, Bruno) → `isFeasible = false` e `generate` lança `IllegalArgumentException` sem consumir o gerador; irmãos podem se tirar; teste qui-quadrado de uniformidade sobre as atribuições válidas de um grupo pequeno com restrições (gerador com semente fixa, tolerância documentada); pior caso "responsável + 5 dependentes + 1" sempre gera; `MAX_ATTEMPTS = 200_000`
- [X] T013 [P] [US1] Criar `test/…/dependent/DependentServiceTest.java` (Mockito, `Clock` fixo, `GroupLock` real sobre mocks): adicionar cria `app_user` `DEPENDENT` e participação com `guardian_id` = usuário da sessão e `confirmedAt` igual ao dele; publica `participants-changed` depois do commit; nome com menos de 2 ou mais de 80 caracteres é barrado na validação; grupo `DRAWN` → `409 group-already-drawn`, `FINISHED` → `409 group-finished`; 100 participantes contando dependentes → `409 group-full`; 6º dependente do mesmo responsável → `409 dependents-limit`; renomear e remover só com `(dependentId, groupId, guardian = sessão)` — dependente de outra pessoa, de outro grupo ou inexistente → `404` igual; remover apaga participação e `app_user` e publica `participants-changed`; não membro → `404`
- [X] T014 [P] [US1] Ampliar `test/…/group/GroupMembershipServiceTest.java`: responsável que sai antes do sorteio leva os dependentes (participações e `app_user`) e publica um único `participants-changed`; organizador remove um dependente pelo `remove` existente (apaga também o `app_user`, sem `membership-revoked` para o dependente); organizador remove um responsável → dependentes saem junto
- [X] T015 [P] [US1] Ampliar `test/…/group/GroupConfirmationServiceTest.java` e `test/…/group/GroupServiceTest.java` (R36, FR-013): confirmar ou voltar a pendente grava o mesmo `confirmed_at` nos dependentes do usuário da sessão; mudar data/horário/local volta todos a pendente menos o organizador **e os dependentes do organizador**; `participants-pending` lista só participantes com conta; `confirmedCount` conta dependentes
- [X] T016 [P] [US1] Ampliar `test/…/draw/DrawServiceTest.java` e `test/…/group/SecretGroupTest.java`: `DrawBlockedReason.NO_VALID_DRAW` (`no-valid-draw`, "Com os dependentes deste grupo, não há sorteio possível. Convide mais participantes.") vem depois de `NOT_ENOUGH_PARTICIPANTS` e antes de `PARTICIPANTS_PENDING`; `draw` com grupo inviável → `422 no-valid-draw` (como os demais bloqueios da 002) sem gravar nenhum par nem mudar o status; `draw` monta os pares proibidos (responsável↔dependente) a partir das participações; o par do dependente é cifrado com AAD `group_id‖id do dependente`
- [X] T017 [P] [US1] Ampliar `test/…/exitrequest/ExitRequestServiceTest.java` (FR-016): aprovar a saída de um responsável com dependentes desfaz o sorteio uma vez, tira o responsável e os dependentes, cria `group_exit_ban` **só para o responsável** (os dependentes não têm identidade fora do grupo, e um ban para eles impediria apagar o `app_user` pela FK), apaga o `app_user` dos dependentes e publica um único `draw-undone`; `membership-revoked` (`exit-approved`) só para o responsável
- [X] T018 [P] [US1] Ampliar `test/…/group/GroupServiceTest.java` (detalhe): `participants` traz `dependent` e `guardianName`; `myDependents` traz só os dependentes do usuário da sessão (id, nome, confirmado); `canAddDependents`/`dependentsBlockedReason` para `group-already-drawn`, `group-finished`, `group-full` e `dependents-limit`; `maxDependents = 5`; `drawBlockedReason = no-valid-draw` quando inviável
- [X] T019 [P] [US1] Criar `test/…/web/DependentControllerWebTest.java`: `POST /groups/{id}/my-dependents` → `201` (`MyDependent`), `PUT …/{dependentId}` → `200`, `DELETE …/{dependentId}` → `204`; exigem sessão e CSRF; nome vazio/curto/longo → `400` com `errors.name`; `404` e `409` do serviço viram problems; ampliar `test/…/web/GroupControllerWebTest.java` para os campos novos de `Participant` e `GroupDetail`
- [X] T020 [P] [US1] Criar `test/…/integration/DependentFlowIT.java` (parte US1): cenários US1-1 a US1-6 pela API, com 3 sessões; o grupo com dependentes respeita o limite de 100; Ana + Lucas + Bruno → `drawBlockedReason = no-valid-draw` e `POST /draw` → `422`; com Carla e Davi, sorteio feito e, decifrando os pares no teste, Ana↔Lucas nunca se tiram; responsável sai/é removido antes do sorteio → dependentes somem e não sobra `app_user` órfão (consulta do [quickstart.md](./quickstart.md) §3.5); `GET /groups` (meus grupos) não muda para ninguém
- [X] T021 [P] [US1] Criar `frontend/src/components/group/DependentsCard.test.tsx` e `frontend/src/components/group/DependentForm.test.tsx`: texto de apresentação; adicionar com nome válido (zod 2–80 e mensagens do servidor); renomear e remover com confirmação só em `OPEN`; botão desabilitado com a mensagem de cada `dependentsBlockedReason`; some com 5 dependentes; "Confirmado junto com você"/"Pendente — confirme a sua participação"; nome com `<b>` aparece como texto
- [X] T022 [P] [US1] Ampliar `frontend/src/pages/GroupPage.members.test.tsx`, `frontend/src/components/group/ConfirmParticipationCard.test.tsx` e `frontend/src/components/group/DrawButton.test.tsx`: "Lucas" com "(dependente de Ana)"; ao remover um responsável, a confirmação lista os dependentes que saem junto; botão "Confirmar participação (você e Lucas)"; `no-valid-draw` desabilita o sorteio com a mensagem da spec

### Implementation for User Story 1

- [X] T023 [US1] Alterar `…/draw/DerangementGenerator.java`: `generate(List<UUID> participants, Set<Pair> forbidden)` (manter a assinatura antiga delegando com conjunto vazio); `static boolean isFeasible(participants, forbidden)` por caminhos aumentantes (Kuhn) sobre o grafo "quem dá → quem pode receber"; `MAX_ATTEMPTS = 200_000`; rejeição de permutações inteiras com Fisher–Yates; Javadoc citando R35 — faz T012 passar
- [X] T024 [US1] Alterar `…/group/DrawBlockedReason.java` (`NO_VALID_DRAW`), `…/group/SecretGroup.java` (o cálculo do motivo passa a receber a viabilidade já calculada) e `…/draw/DrawService.java` (monta os pares proibidos a partir de `guardian_id`, chama `isFeasible` antes de qualquer escrita, `UnprocessableException("no-valid-draw", …)`) — faz T016 passar
- [X] T025 [P] [US1] Criar `…/dependent/dto/DependentRequest.java` (`@NotBlank` e `@Size(min = 2, max = 80)` aplicados ao nome após `strip`, mensagem "Informe um nome de 2 a 80 caracteres.") e `…/dependent/dto/MyDependent.java` (`id`, `name`, `confirmed`, `wishlist`, `exitRequest`; nesta história `wishlist` vazia editável e `exitRequest` nulo)
- [X] T026 [US1] Criar `…/dependent/DependentLookup.java` (busca `(dependentId, groupId, guardian = sessão)`; `NotFoundException` igual para todos os casos) e `…/dependent/DependentService.java` (`add`, `rename`, `remove`, com `GroupLock`, regras de T013 e evento `participants-changed` após o commit) — faz T013 passar
- [X] T027 [US1] Criar `…/dependent/DependentController.java` com `POST /api/v1/groups/{groupId}/my-dependents`, `PUT …/{dependentId}` e `DELETE …/{dependentId}`, usuário sempre de `CurrentUser` — faz T019 (parte do controller) passar
- [X] T028 [US1] Alterar `…/group/GroupMembershipService.java`: `leave` e `remove` coletam os ids dos dependentes antes de apagar a participação (o `CASCADE` apaga as deles) e apagam os `app_user` dependentes sem `exit_request`; remover um dependente pelo organizador apaga também o `app_user` — faz T014 passar
- [X] T029 [US1] Alterar `…/group/GroupConfirmationService.java` e `…/group/GroupMemberRepository.java` (`resetConfirmations` preserva o organizador e os dependentes dele; `mirrorConfirmations(groupId, guardianId, confirmedAt)`); a lista de pendentes só com contas — faz T015 passar
- [X] T030 [US1] Alterar `…/exitrequest/ExitRequestService.java` (`approve`): quando quem sai tem dependentes, desfazer o sorteio uma vez, criar `group_exit_ban` só para o responsável, apagar as participações e os `app_user` dependentes sem `exit_request` próprio, publicar um único `draw-undone` — faz T017 passar
- [X] T031 [US1] Alterar `…/group/dto/Participant.java` (`dependent`, `guardianName`, com construtor antigo mantido), `…/group/dto/GroupDetail.java` (`myDependents`, `maxDependents`, `canAddDependents`, `dependentsBlockedReason`, com construtor antigo mantido) e `…/group/GroupService.java` (monta tudo a partir da sessão; calcula a viabilidade para o `drawBlockedReason`) — faz T018, T019 e T020 passarem
- [X] T032 [P] [US1] Criar `frontend/src/api/dependents.ts` (`addDependent`, `renameDependent`, `removeDependent`) e ampliar `frontend/src/lib/schemas.ts` com o zod do nome (2–80 após `trim`)
- [X] T033 [US1] Criar `frontend/src/components/group/DependentForm.tsx` e `frontend/src/components/group/DependentsCard.tsx` (cartão "Meus dependentes" conforme [ui-routes.md](./contracts/ui-routes.md)) e usá-los em `frontend/src/pages/GroupPage.tsx`; alterar `frontend/src/components/group/ParticipantList.tsx` ("(dependente de Ana)" e aviso ao remover responsável), `frontend/src/components/group/ConfirmParticipationCard.tsx` (texto com os dependentes) e `frontend/src/components/group/DrawButton.tsx` (mensagem de `no-valid-draw`) — faz T021 e T022 passarem

**Checkpoint**: US1 funciona sozinha; dependentes entram, confirmam e saem com o responsável; o sorteio respeita as restrições; ninguém vê resultado de dependente (ainda não há rota para isso).

---

## Phase 4: User Story 2 - O responsável vê quem o dependente tirou (Priority: P1)

**Goal**: depois do sorteio, o responsável revela, num cartão separado por dependente, quem cada um tirou (nome, valor do presente e lista de desejos); ninguém mais vê esses resultados.

**Independent Test**: num grupo sorteado com Ana (responsável do Lucas), Bruno e Carla, Ana revela o próprio amigo e o do Lucas; Bruno e Carla revelam só o próprio; nenhuma outra tela, resposta ou aviso mostra o resultado do Lucas para quem não é a Ana.

### Tests for User Story 2 ⚠️

- [X] T034 [P] [US2] Ampliar `test/…/draw/SecretFriendServiceTest.java`: `getMyDependents(sessionUserId, groupId)` decifra o par de cada dependente com `guardian_id = sessão` (nunca de parâmetro), na ordem de adição; cada item traz `dependentId`, `dependentName` e `secretFriend` (nome, lista, `wishlistUpdatedAt`) sem o id de quem foi tirado; sem dependentes → lista vazia; `FINISHED` também responde; `OPEN` → `409 group-not-drawn` sem decifrar nada; não membro → `404`
- [X] T035 [P] [US2] Ampliar `test/…/web/DrawControllerWebTest.java`: `GET /groups/{id}/my-dependents/secret-friends` serializa o array conforme [contracts/openapi.yaml](./contracts/openapi.yaml), com `Cache-Control: no-store`; a rota não aceita nenhum parâmetro de id (parâmetros extras são ignorados e não mudam o resultado)
- [X] T036 [P] [US2] Ampliar `test/…/integration/SecrecyIT.java` (SC-002): com Ana (Lucas), Bruno, Carla e o organizador, só Ana recebe o resultado do Lucas; Bruno, Carla, o organizador e quem foi tirado pelo Lucas recebem `[]` em `my-dependents/secret-friends`; nenhuma resposta de `GET /groups`, `GET /groups/{id}`, `my-secret-friend`, pedidos de saída, avisos ou eventos SSE contém o par do Lucas; `PUT`/`DELETE`/lista/saída do Lucas por outra sessão → `404`; trocar `?userId=`/`?dependentId=` na URL não muda o resultado; os logs não contêm o nome de quem o Lucas tirou associado a ele; atualizar a verificação de chaves das respostas
- [X] T037 [P] [US2] Ampliar `test/…/integration/DependentFlowIT.java` (parte US2): cenários US2-1 a US2-5; quem tirou o Lucas vê "Lucas" e a lista dele em `my-secret-friend`; após sorteio desfeito, `my-dependents/secret-friends` → `409 group-not-drawn`
- [X] T038 [P] [US2] Criar `frontend/src/components/group/DependentSecretFriendCard.test.tsx` e ampliar `frontend/src/pages/GroupPage.drawn.test.tsx`: um cartão "Amigo secreto do Lucas" por dependente, todos ocultos; o primeiro "Revelar" busca a rota uma vez e só o cartão revelado mostra o resultado (nome, valor do presente, `WishlistView`); "Ocultar" esconde; ao ocultar o último cartão a consulta é descartada; `draw-undone` apaga tudo; nada em `localStorage`/`sessionStorage`; sem dependentes, nenhum cartão e nenhuma chamada

### Implementation for User Story 2

- [X] T039 [US2] Criar `…/dependent/dto/DependentSecretFriend.java` e alterar `…/draw/SecretFriendService.java` (`getMyDependents`, reaproveitando a resolução do par e da lista de `getMine` com o id do dependente vindo de `findDependents(groupId, sessão)`; nunca logar resultado) e `…/draw/DrawController.java` (`GET /api/v1/groups/{groupId}/my-dependents/secret-friends`, `Cache-Control: no-store`) — faz T034 a T037 passarem
- [X] T040 [US2] Ampliar `frontend/src/api/dependents.ts` (`getDependentsSecretFriends`) e criar `frontend/src/components/group/DependentSecretFriendCard.tsx` (mesmo visual do `SecretFriendCard`, TanStack Query com `gcTime: 0`, consulta compartilhada entre os cartões e habilitada só enquanto algum estiver revelado); usar em `frontend/src/pages/GroupPage.tsx` logo abaixo de "Seu amigo secreto" — faz T038 passar

**Checkpoint**: US1 + US2 = MVP; o responsável vê só os resultados dos próprios dependentes, com o sigilo testado.

---

## Phase 5: User Story 3 - O responsável age pelo dependente (Priority: P2)

**Goal**: o responsável monta a lista de desejos de cada dependente e pede (ou cancela) a saída dele depois do sorteio; o aviso da decisão chega ao responsável; depois de uma saída aprovada, aquele responsável não adiciona mais dependentes no grupo. (A confirmação e a saída junto com o responsável já vieram na US1.)

**Independent Test**: Ana monta a lista do Lucas; depois do sorteio, quem tirou o Lucas vê a lista; Ana pede a saída do Lucas, o organizador vê "Lucas (dependente de Ana)" e aprova; o sorteio é desfeito; Ana recebe o aviso e não consegue adicionar outro dependente, mas Bruno consegue.

### Tests for User Story 3 ⚠️

- [X] T041 [P] [US3] Ampliar `test/…/wishlist/WishlistServiceTest.java`: as operações recebem o **dono** já resolvido; para dependente, mesmas regras (5 itens, `wishlist-full`, `group-finished`, `touchWishlist` na participação do dependente); dependente de outra pessoa → `404` (via `DependentLookup`)
- [X] T042 [P] [US3] Ampliar `test/…/exitrequest/ExitRequestServiceTest.java` e `test/…/exitrequest/ExitRequestNoticeServiceTest.java` (R37): pedir a saída do dependente grava `user_id` = dependente e `requested_by` = responsável; mesmas regras da 002 (`group-not-drawn`, `exit-request-already-pending`, `group-finished`); cancelar só o pedido pendente do próprio dependente; aprovar desfaz o sorteio, cria o `group_exit_ban` do dependente, apaga a participação e **mantém** o `app_user` (referenciado pelo pedido); avisos são buscados por `requested_by` e trazem `subjectName`; o `acknowledge` (`POST /exit-request-notices/{id}/ack`) é aceito do responsável (`requested_by`) e responde `404` para qualquer outra pessoa; pedidos pendentes dos dependentes são cancelados quando a saída do responsável é aprovada
- [X] T043 [P] [US3] Ampliar `test/…/dependent/DependentServiceTest.java` (Clarifications Q3): com pedido `APPROVED` de um dependente da Ana no grupo, Ana → `409 dependents-locked` ("Depois da saída de um dependente seu, não dá para adicionar outros neste grupo."); Bruno continua adicionando; a saída aprovada da **própria** Ana (sem dependente) não conta; `GroupDetail.dependentsBlockedReason = dependents-locked`
- [X] T044 [P] [US3] Criar `test/…/web/DependentWishlistControllerWebTest.java` e ampliar `test/…/web/ExitRequestControllerWebTest.java`: rotas `my-dependents/{dependentId}/wishlist/items[/{itemId}]` e `my-dependents/{dependentId}/exit-request` (`POST` → `201`, `DELETE` → `204`) com sessão, CSRF, `400`/`404`/`409` como no contrato; `ExitRequest.guardianName` e `ExitRequestNotice.subjectName` serializados
- [X] T045 [P] [US3] Ampliar `test/…/integration/DependentFlowIT.java` (parte US3): cenários US3-3 e US3-4 de ponta a ponta; `myDependents[].wishlist` e `myDependents[].exitRequest` no detalhe da Ana; o organizador vê o pedido com `guardianName`; Ana recebe o aviso; `dependents-locked` só para Ana; ampliar `test/…/integration/ExitRequestFlowIT.java` para confirmar que pedidos de contas continuam como na 002
- [X] T046 [P] [US3] Ampliar `frontend/src/components/group/WishlistEditor.test.tsx`, `frontend/src/components/group/DependentsCard.test.tsx`, `frontend/src/components/group/ExitRequestsList.test.tsx` e `frontend/src/hooks/useExitRequestNotices.test.tsx`: "Lista de desejos de Lucas" usa as rotas do dependente; "Pedir saída de Lucas"/"Saída de Lucas pendente"/"Cancelar pedido" em `DRAWN`; organizador vê "Lucas (dependente de Ana)"; aviso "A saída do Lucas … foi aprovada"; mensagem de `dependents-locked`

### Implementation for User Story 3

- [X] T047 [US3] Alterar `…/wishlist/WishlistService.java` (operações por dono resolvido; `mine` passa o usuário da sessão) e criar `…/wishlist/DependentWishlistController.java` (`/api/v1/groups/{groupId}/my-dependents/{dependentId}/wishlist/items[/{itemId}]`, dono via `DependentLookup`); `GroupService` preenche `myDependents[].wishlist` — faz T041 passar
- [X] T048 [US3] Alterar `…/exitrequest/ExitRequestService.java` (`requestForDependent`, `cancelForDependent`, aprovação mantendo o `app_user` do dependente, cancelamento dos pedidos dos dependentes na saída do responsável), `…/exitrequest/ExitRequestNoticeService.java` e `…/exitrequest/ExitRequestRepository.java` (avisos e `acknowledge` por `requested_by`, `subjectName`; `existsApprovedDependentExit(groupId, guardianId)`), `…/exitrequest/ExitRequestController.java` (`POST`/`DELETE /api/v1/groups/{groupId}/my-dependents/{dependentId}/exit-request`) e os DTOs `ExitRequestResponse` (`guardianName`) e `ExitRequestNotice` (`subjectName`), com construtores antigos mantidos — faz T042 e T044 passarem
- [X] T049 [US3] Alterar `…/dependent/DependentService.java` e `…/group/GroupService.java`: regra `dependents-locked` ao adicionar e no `dependentsBlockedReason`; `myDependents[].exitRequest` — faz T043 e T045 passarem
- [X] T050 [US3] Alterar `frontend/src/components/group/WishlistEditor.tsx` (dono como parâmetro: funções de API e título), `frontend/src/components/group/DependentsCard.tsx` (lista e pedido de saída por dependente), `frontend/src/components/group/ExitRequestPanel.tsx`/`ExitRequestsList.tsx` (nome do responsável), `frontend/src/hooks/useExitRequestNotices.ts` (`subjectName`) e `frontend/src/api/dependents.ts`/`exitRequests.ts` — faz T046 passar

**Checkpoint**: as três histórias funcionam juntas.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T051 [P] Atualizar `README.md` (dependentes: quem adiciona, quem vê o quê, regras do sorteio) e revisar o Javadoc dos controllers/DTOs novos e a descrição em `…/config/OpenApiConfig.java` (citar o contrato 004)
- [ ] T052 Voltar `test/…/integration/OpenApiContractIT.java` à igualdade completa (rotas geradas = união 001 + 002 + 003 + 004)
- [ ] T053 Ampliar `test/…/integration/DrawPerformanceIT.java`: sorteio de 100 participantes com 15 responsáveis e 5 dependentes cada em < 1 s no servidor (plan.md, Performance Goals)
- [ ] T054 Rodar `mvn -B verify` e `npm run lint && npm run format:check && npm run test:coverage && npm run build` nos containers; corrigir até ficar verde com cobertura ≥ 80% nos dois
- [ ] T055 Executar o roteiro manual do [quickstart.md](./quickstart.md) (§1 migração, §2 passos 1–17 com uma janela a 360px, §3 sigilo) e registrar o resultado no PR
- [ ] T056 Revisão final contra a constituição v1.3.0 (Princípios I a VIII) com a checklist do PR template, conferindo em especial: nenhuma rota devolve resultado de dependente a partir de id do cliente; dependente nunca autentica; sorteio só por rejeição; nenhum evento SSE novo; nenhuma dependência nova no `pom.xml` e no `package.json`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup; **bloqueia todas as histórias**.
- **US1 (Phase 3)**: depende da Foundational.
- **US2 (Phase 4)**: depende da US1 (precisa de dependentes sorteados) — os testes podem ser
  escritos em paralelo à US1.
- **US3 (Phase 5)**: depende da US1 (`DependentLookup`, `DependentsCard`); independente da US2.
- **Polish (Phase 6)**: depende de todas as histórias.

```text
Setup ─▶ Foundational ─▶ US1 ─┬─▶ US2 ─┐
                              └─▶ US3 ─┴─▶ Polish
```

### Within Each User Story

- Testes primeiro, vendo-os **falhar**.
- Migração → entidades e repositórios → serviços → controllers → frontend (api → componentes → páginas).
- Commit ao fim de cada tarefa ou grupo lógico (Conventional Commits).

### Parallel Opportunities

- Setup: T002 em paralelo com T001.
- Foundational: T003, T004 e T005 primeiro; depois T007, T008, T009 e T011 em paralelo (T010 depois
  de T008).
- Em cada história, todas as tarefas de teste [P] podem ser escritas em paralelo.
- US1: T023 (gerador) e T025/T032 (DTOs, API do frontend) em paralelo; T026 → T027.
- US2 e US3 podem andar em paralelo depois da US1, exceto em `GroupPage.tsx`, `GroupService.java`
  e `DependentsCard.tsx` (um de cada vez).

---

## Parallel Example: User Story 1

```bash
# Testes primeiro, em paralelo:
Task: "T012 DerangementGeneratorTest (restrições, viabilidade, uniformidade)"
Task: "T013 DependentServiceTest"
Task: "T014 GroupMembershipServiceTest (dependentes saem junto)"
Task: "T015 Confirmação espelhada"
Task: "T016 DrawServiceTest (no-valid-draw)"
Task: "T019 DependentControllerWebTest"
Task: "T021 DependentsCard.test.tsx"

# Depois, peças independentes em paralelo:
Task: "T023 DerangementGenerator"
Task: "T025 DTOs do dependente"
Task: "T032 api/dependents.ts + zod"
```

---

## Implementation Strategy

### MVP

**MVP = Setup + Foundational + US1 + US2** (as duas histórias P1): sem a US2, ninguém sabe para
quem o dependente vai dar o presente. A US3 (P2) completa o ciclo (lista e saída do dependente).

1. Setup → Foundational → migração V8 validada.
2. US1 → **parar e validar** (adicionar, confirmar, sortear com restrições; ninguém vê resultado
   de dependente).
3. US2 → **parar e validar** (responsável vê os resultados; sigilo) → **MVP pronto**.
4. US3 → validar (lista e saída do dependente) → Polish → PR para a `main`.

### Sugestão de entregas (PRs menores)

| PR | Conteúdo |
|----|----------|
| 1 | Phase 1 + Phase 2 + US1 |
| 2 | US2 |
| 3 | US3 + Polish |

Cada PR passa pelo CI, pelas varreduras e pela aprovação manual (`aprovacao-manual`).

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- [USn] mapeia a tarefa para a história da [spec.md](./spec.md) (com a exceção explicada no topo).
- Todo teste deve falhar antes da implementação correspondente.
- Evitar: tarefas vagas, dois agentes no mesmo arquivo ao mesmo tempo e qualquer atalho que exponha
  o resultado de um dependente para quem não é o responsável.
