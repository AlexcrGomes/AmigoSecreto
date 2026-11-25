---

description: "Lista de tarefas da feature 003 — lista de desejos e valor do presente"
---

# Tasks: Lista de Desejos

**Input**: Design documents from `/specs/003-lista-desejos/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **OBRIGATÓRIOS** (Constituição IV). Em cada história, as tarefas de teste vêm
**antes** da implementação e MUST falhar antes do código existir (Red → Green → Refactor).
Backend: JUnit 5 + Mockito + AssertJ; integração com Testcontainers (PostgreSQL 18.7). Frontend:
Vitest + React Testing Library + MSW. Gate de cobertura: 80% de linhas (JaCoCo e Vitest).

**Organization**: tarefas agrupadas por história de usuário (US1–US3 da spec).

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

- **Sigilo (Constituição I)**: a lista de desejos de outra pessoa só sai em
  `GET /groups/{groupId}/my-secret-friend`, a partir do par do usuário da sessão (research R27).
  Nenhuma rota recebe id de usuário para ler lista; `GroupDetail` traz só a lista do próprio
  usuário (`myWishlist`); nenhuma resposta traz o id de quem foi tirado; nenhum evento SSE, log ou
  mensagem de erro contém itens de lista nem registra quem viu uma lista (FR-015, SC-005).
- **Lock único**: toda escrita na lista ou no valor do presente trava o grupo pelo `GroupLock`
  (finaliza se o evento passou) e recusa grupo finalizado com `409 group-finished` (feature 002).
- **Links**: só `http`/`https` absolutos com host, até 500 caracteres; o servidor **nunca**
  acessa o link (research R29).
- **Sem dependências novas** nem atualização de versões (política do projeto e plan.md).
- Mensagens em **pt-BR**; erros em `application/problem+json` com `type` `/problems/<código>`.
- Texto digitado pelo usuário (descrição, link) nunca é renderizado como HTML (FR-008).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: partir de uma base verde. Não há dependência nova nem mudança de infraestrutura.

- [X] T001 Confirmar a base: na branch `003-lista-desejos`, rodar `mvn -B verify` (backend) e `npm run lint && npm run format:check && npm run test:coverage` (frontend) nos containers e registrar que tudo está verde antes de mudar código
- [X] T002 [P] Atualizar `test/…/integration/OpenApiContractIT.java` para ler também `../specs/003-lista-desejos/contracts/openapi.yaml` e montar a união 001 + 002 + 003. Enquanto as histórias são entregues em PRs separados, o teste exige só que **toda rota gerada esteja documentada** (geradas ⊆ união) e que todas as rotas de 001 + 002 existam; a igualdade completa volta na T040

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esquema do banco e mapeamentos que TODAS as histórias usam.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase terminar.

### Tests (escrever primeiro e ver falhar)

- [X] T003 [P] Criar `test/…/integration/MigrationV7IT.java` (estende `AbstractIntegrationTest`, cria usuário e grupo pela API): `wish_item` existe; `gift_value` aceita `NULL`, 1 e 100000 e recusa 0 e 100001 (`ck_secret_group_gift_value`); `wish_item.description` acima de 120 caracteres é recusada; apagar a linha de `group_member` apaga os itens do membro (`ON DELETE CASCADE`, FR-018); inserir item para um par `(group_id, user_id)` sem participação falha pela FK

### Implementation

- [X] T004 Criar `backend/src/main/resources/db/migration/V7__wishlist_and_gift_value.sql` conforme [data-model.md](./data-model.md): `secret_group.gift_value integer NULL` com `CONSTRAINT ck_secret_group_gift_value CHECK (gift_value BETWEEN 1 AND 100000)`; `group_member.wishlist_updated_at timestamptz NULL`; tabela `wish_item (id uuid PRIMARY KEY, group_id uuid NOT NULL, user_id uuid NOT NULL, description varchar(120) NOT NULL, url varchar(500) NULL, created_at timestamptz NOT NULL, FOREIGN KEY (group_id, user_id) REFERENCES group_member (group_id, user_id) ON DELETE CASCADE)`; índice `ix_wish_item_owner (group_id, user_id, created_at)` — faz T003 passar
- [X] T005 [P] Criar `…/wishlist/WishItem.java` (entidade `wish_item`, `Persistable<UUID>` como as demais; construtor `(groupId, userId, description, url, createdAt)` com `strip` na descrição e link vazio → `null`; `update(description, url)`; `toString` só com `id` e `groupId`) e `…/wishlist/WishItemRepository.java` (`findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc`, `countByGroupIdAndUserId`, `findByIdAndGroupIdAndUserId`) — **nenhum** método que liste itens de um grupo inteiro
- [X] T006 [P] Alterar `…/group/GroupMember.java`: mapear `wishlist_updated_at` (`getWishlistUpdatedAt()`) e `touchWishlist(Instant now)`; ajustar `test/…/group/SecretGroupTest.java` (seção de `GroupMember`) com o teste de `touchWishlist`
- [X] T007 [P] Alterar `…/group/SecretGroup.java`: mapear `gift_value` (`getGiftValue()`, `Integer`, sem regra ainda — a regra vem na US3)
- [X] T008 [P] Alterar `frontend/src/api/groups.ts` e `frontend/src/test/groups.ts`: tipos `WishItem { id; description; url: string | null }`, `MyWishlist { items; maxItems; editable }`, `GroupDetail.myWishlist`, `EventDetails.giftValue: number | null`, `SecretFriend { name; wishlist: { description; url }[]; wishlistUpdatedAt: string | null }` conforme [contracts/openapi.yaml](./contracts/openapi.yaml); fábricas de teste com `myWishlist` vazio editável e `giftValue: null`

**Checkpoint**: banco migrado, entidades mapeadas (`ddl-auto: validate` verde), tipos do frontend prontos.

---

## Phase 3: User Story 1 - Montar a própria lista de desejos (Priority: P1) 🎯 MVP

**Goal**: o participante adiciona, edita e remove até 5 sugestões (descrição e link opcional) na própria lista de cada grupo; só ele a vê; em grupo finalizado ela fica somente leitura.

**Independent Test**: um participante cadastra três itens (dois só com descrição e um com link), edita um e remove outro; ao reabrir o grupo, vê a lista atualizada; outro participante do mesmo grupo não vê nada dela.

### Tests for User Story 1 ⚠️

- [X] T009 [P] [US1] Criar `test/…/wishlist/WishItemUrlTest.java`: aceita `https://loja.com/p?id=1` e `http://a.b`; recusa `javascript:alert(1)`, `data:text/html,x`, `ftp://x`, `//loja.com`, `loja.com`, texto com espaços e links acima de 500 caracteres; vazio/só espaços vira `null` (R29)
- [X] T010 [P] [US1] Criar `test/…/wishlist/WishlistServiceTest.java` (Mockito, `Clock` fixo, `GroupLock` real sobre mocks): adicionar grava item e chama `touchWishlist(now)` na participação do usuário da sessão; 6º item → `409 wishlist-full` ("A lista tem no máximo 5 sugestões."); alterar e remover só item de `(id, groupId, userId da sessão)` — item de outra pessoa ou de outro grupo → `404` igual; remover também chama `touchWishlist`; grupo `DRAWN` permite editar; grupo `FINISHED` → `409 group-finished` em adicionar, alterar e remover; grupo vencido é finalizado pela tentativa (`finishIfDue`); não membro → `404`; nenhuma operação publica evento
- [X] T011 [P] [US1] Ampliar `test/…/group/GroupServiceTest.java`: `detail()` traz `myWishlist` com os itens do usuário da sessão na ordem de cadastro, `maxItems = 5` e `editable = false` só em `FINISHED`; os itens de outro participante nunca aparecem no `GroupDetail` de ninguém
- [X] T012 [P] [US1] Criar `test/…/web/WishlistControllerWebTest.java`: as três rotas de [contracts/openapi.yaml](./contracts/openapi.yaml) exigem sessão e CSRF; `POST` → `201` com a lista; `PUT` → `200`; `DELETE` → `204`; descrição vazia/longa e link inválido → `400` com `errors` por campo; `404` e `409` vindos do serviço viram problems
- [X] T013 [P] [US1] Criar `test/…/integration/WishlistFlowIT.java` (parte US1): cenários US1-1 a US1-7 pela API; duas sessões do mesmo usuário alterando itens diferentes não se sobrescrevem (Edge Case); sair do grupo apaga os itens (FR-018); listas de grupos diferentes são independentes
- [X] T014 [P] [US1] Criar `frontend/src/components/group/WishlistEditor.test.tsx`: lista vazia com o convite; adicionar (descrição obrigatória, link opcional, mensagens de erro do zod e do servidor); editar; remover com confirmação; contador "N de 5" e "Adicionar sugestão" some com 5 itens; somente leitura quando `editable = false`; descrição com `<b>` aparece como texto

### Implementation for User Story 1

- [X] T015 [P] [US1] Criar `…/wishlist/WishItemUrl.java` (normaliza e valida o link com `java.net.URI`: absoluto, esquema `http`/`https`, host presente, até 500 caracteres; nunca abre conexão) — faz T009 passar
- [X] T016 [P] [US1] Criar `…/wishlist/dto/WishItemRequest.java` (`@NotBlank @Size(max = 120) description`, `@Size(max = 500) url` + validação de `WishItemUrl` com a mensagem "Informe um link que comece com http:// ou https://."), `WishItemResponse(id, description, url)` e `MyWishlist(items, maxItems = 5, editable)`
- [X] T017 [US1] Criar `…/wishlist/WishlistService.java`: `add`, `update`, `remove`, todos com `@Transactional(noRollbackFor = GroupFinishedException.class)`, `GroupLock.acquire`, membro (404), `FINISHED` (409), limite de 5 (`ConflictException("wishlist-full", …)`), `touchWishlist(clock.instant())`; nenhum evento publicado — faz T010 passar (a própria lista no detalhe é montada pelo `GroupService`, T019)
- [X] T018 [US1] Criar `…/wishlist/WishlistController.java` com `POST /api/v1/groups/{groupId}/wishlist/mine/items`, `PUT …/items/{itemId}` e `DELETE …/items/{itemId}`, usuário sempre de `CurrentUser` — faz T012 passar
- [X] T019 [US1] Alterar `…/group/dto/GroupDetail.java` e `…/group/GroupService.java`: campo `myWishlist` montado com os itens do usuário da sessão (via `WishItemRepository`, sem dependência circular com o `WishlistService`); ajustar os construtores de `GroupDetail` nos testes existentes — faz T011 e T013 passarem
- [X] T020 [P] [US1] Criar `frontend/src/api/wishlist.ts` (`addWishItem`, `updateWishItem`, `removeWishItem`) e ampliar `frontend/src/lib/schemas.ts` com o zod do item (descrição 1–120, link opcional `http(s)://`, até 500)
- [X] T021 [US1] Criar `frontend/src/components/group/WishlistEditor.tsx` (cartão "Minha lista de desejos", "Só você e quem tirar você veem esta lista.", itens com Editar/Remover via `ConfirmModal`, formulário com react-hook-form + zod, contador, somente leitura) e usá-lo em `frontend/src/pages/GroupPage.tsx` para todos os status; após cada escrita, atualizar a consulta do grupo com a lista devolvida — faz T014 passar

**Checkpoint**: US1 funciona sozinha; ninguém vê a lista de outra pessoa.

---

## Phase 4: User Story 2 - Ver a lista de desejos de quem tirei (Priority: P1)

**Goal**: depois do sorteio, ao revelar o nome, o participante vê a lista de quem tirou (e só dela), com "Atualizada em dd/mm" quando mudou depois do sorteio; o nome e a lista somem juntos.

**Independent Test**: em um grupo sorteado com 3 participantes e listas diferentes, cada um revela e vê exatamente a lista de quem tirou; nenhuma outra resposta, tela ou evento mostra a lista de alguém para quem não a tirou.

### Tests for User Story 2 ⚠️

- [ ] T022 [P] [US2] Ampliar `test/…/draw/SecretFriendServiceTest.java`: a resposta traz a lista de quem o usuário tirou (busca por `(groupId, receiverId)` com o id decifrado do par dele, nunca de um parâmetro); lista vazia → `[]`; `wishlistUpdatedAt` preenchido só se `wishlist_updated_at > drawn_at`, senão `null`; o id de quem foi tirado não aparece na resposta; `FINISHED` também traz a lista; `OPEN` → `409 group-not-drawn` sem consultar itens
- [ ] T023 [P] [US2] Ampliar `test/…/web/DrawControllerWebTest.java`: `my-secret-friend` serializa `name`, `wishlist` (`description`, `url`) e `wishlistUpdatedAt`, sem nenhum id, com `Cache-Control: no-store`
- [ ] T024 [P] [US2] Ampliar `test/…/integration/SecrecyIT.java` (SC-002, SC-005): com 3 participantes e listas diferentes, cada um recebe só a lista de quem tirou; o organizador não recebe a de ninguém fora do próprio resultado; `GET /groups`, `GET /groups/{id}` e os eventos SSE não contêm descrição de item de outra pessoa; os logs não contêm descrições de itens; trocar `?userId=` na URL não muda o resultado; o `GroupDetail` e a lista de grupos não têm nenhum campo sobre a lista de outra pessoa (nem contagem nem indicador de que ela tem itens — FR-016); atualizar a verificação de chaves de `my-secret-friend` para `name`, `wishlist`, `wishlistUpdatedAt`
- [ ] T025 [P] [US2] Ampliar `test/…/integration/WishlistFlowIT.java` (parte US2): cenários US2-1 a US2-7; depois de um sorteio desfeito por saída aprovada (feature 002), quem tirava a pessoa recebe `409` em `my-secret-friend` e, após o novo sorteio, só quem a tirar vê a lista (FR-013); a lista de quem saiu foi apagada
- [ ] T026 [P] [US2] Criar `frontend/src/components/group/WishlistView.test.tsx` e `frontend/src/components/group/ExternalLinkModal.test.tsx`, e ampliar `frontend/src/components/group/SecretFriendCard.test.tsx`: lista com itens, vazia ("Ainda não há sugestões de presente."), "Atualizada em dd/mm" só quando `wishlistUpdatedAt` vem preenchido; link abre o modal "Você vai abrir um site externo" e "Abrir" chama `window.open` com `noopener,noreferrer`; "Ocultar" esconde nome e lista; `draw-undone` apaga os dois

### Implementation for User Story 2

- [ ] T027 [US2] Alterar `…/draw/dto/SecretFriendResponse.java` (`name`, `List<FriendWishItem> wishlist`, `Instant wishlistUpdatedAt`) e criar `…/wishlist/dto/FriendWishItem.java` (`description`, `url`, sem id)
- [ ] T028 [US2] Alterar `…/draw/SecretFriendService.java`: depois de decifrar o par, buscar os itens de `(groupId, receiverId)` no `WishItemRepository` e a `wishlistUpdatedAt` da participação de quem foi tirado; comparar com `drawnAt`; nunca logar nada disso — faz T022, T023, T024 e T025 passarem
- [ ] T029 [P] [US2] Criar `frontend/src/components/group/ExternalLinkModal.tsx` (modal com o endereço, "Abrir" → `window.open(url, '_blank', 'noopener,noreferrer')`, "Cancelar") e `frontend/src/components/group/WishlistView.tsx` (itens, vazio, "Atualizada em" formatado em horário de Brasília)
- [ ] T030 [US2] Alterar `frontend/src/components/group/SecretFriendCard.tsx`: mostrar `WishlistView` abaixo do nome, com os dados da mesma consulta (`gcTime: 0`), e sumir junto em "Ocultar" — faz T026 passar

**Checkpoint**: US1 + US2 entregam a lista de ponta a ponta, com o sigilo testado.

---

## Phase 5: User Story 3 - Valor do presente definido no grupo (Priority: P2)

**Goal**: o organizador informa um valor do presente opcional (reais inteiros, 1–100.000) ao criar ou editar o grupo antes do sorteio; todos o veem; depois do sorteio, fica fixo; mudar o valor não reseta confirmações.

**Independent Test**: o organizador cria o grupo com R$ 80; um participante vê o valor; o organizador muda para R$ 100 antes do sorteio e as confirmações continuam; depois do sorteio, um valor diferente é recusado e o valor aparece junto da lista revelada.

### Tests for User Story 3 ⚠️

- [ ] T031 [P] [US3] Ampliar `test/…/group/SecretGroupTest.java`: com o grupo `OPEN`, informar, alterar e remover o valor; com `DRAWN`, o mesmo valor é aceito e um diferente → `409 gift-value-locked` ("O valor do presente não muda depois do sorteio."); com `FINISHED` → `409 group-finished`; mudar o valor não muda `detailsVersion`
- [ ] T032 [P] [US3] Ampliar `test/…/group/GroupServiceTest.java` e `test/…/web/GroupControllerWebTest.java`: criar e editar com `giftValue`; validação (`0`, `100001`, decimal → `400` com a mensagem de [data-model.md](./data-model.md)); alterar só o valor com o grupo aberto publica `GroupUpdatedEvent(confirmationsReset = false)` e não chama `resetConfirmations` (FR-023); `EventDetailsResponse.giftValue` no detalhe de todos os participantes
- [ ] T033 [P] [US3] Ampliar `test/…/integration/WishlistFlowIT.java` (parte US3): cenários US3-1 a US3-6; sorteio desfeito → valor editável de novo (Edge Case)
- [ ] T034 [P] [US3] Ampliar `frontend/src/components/group/EventDetailsForm.test.tsx`, `frontend/src/components/group/EventDetailsCard.test.tsx` e `frontend/src/pages/CreateGroupPage.test.tsx`: campo "Valor do presente (R$)" opcional com validação; desabilitado com a explicação quando o grupo está sorteado; mudar só o valor não mostra "Todos os participantes precisarão confirmar de novo."; o cartão mostra "Valor do presente: R$ 80"; ampliar `SecretFriendCard.test.tsx` para mostrar o valor junto da lista

### Implementation for User Story 3

- [ ] T035 [US3] Alterar `…/group/SecretGroup.java` (`updateGiftValue(Integer)` com a regra de status) e `…/group/dto/EventDetailsRequest.java`/`EventDetailsResponse.java`/`CreateGroupRequest.java` (`giftValue` opcional, `@Min(1) @Max(100000)`); `GroupService.create` e `updateEventDetails` aplicam o valor sem afetar `scheduleChanged` — faz T031 e T032 passarem
- [ ] T036 [US3] Alterar `frontend/src/lib/schemas.ts`, `frontend/src/components/group/EventDetailsForm.tsx` (campo, desabilitado após o sorteio, fora da comparação que gera o aviso de reconfirmação), `frontend/src/components/group/EventDetailsCard.tsx`, `frontend/src/pages/CreateGroupPage.tsx` e `frontend/src/components/group/SecretFriendCard.tsx` (valor junto da lista) — faz T034 passar; T033 passa com T035

**Checkpoint**: as três histórias funcionam juntas.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T037 [P] Atualizar `README.md`: descrever a lista de desejos (quem vê o quê) e o valor do presente
- [ ] T038 [P] Revisar o Javadoc dos controllers e DTOs novos e a descrição em `…/config/OpenApiConfig.java` (citar o contrato 003)
- [ ] T039 Rodar `mvn -B verify` e `npm run lint && npm run format:check && npm run test:coverage && npm run build` nos containers; corrigir até ficar verde com cobertura ≥ 80% nos dois
- [ ] T040 Voltar `test/…/integration/OpenApiContractIT.java` à igualdade completa (rotas geradas = união 001 + 002 + 003)
- [ ] T041 Executar o roteiro manual do [quickstart.md](./quickstart.md) (§1 migração, §2 passos 1–17 com uma janela a 360px, §3 sigilo) e registrar o resultado no PR
- [ ] T042 Revisão final contra a constituição (Princípios I a VIII) com a checklist do PR template, conferindo em especial: nenhuma rota lê lista por id de usuário; `GroupDetail` só com `myWishlist`; nenhum evento SSE novo; o servidor nunca acessa links; nenhuma dependência nova no `pom.xml` e no `package.json`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup; **bloqueia todas as histórias**.
- **US1 (Phase 3)**: depende da Foundational.
- **US2 (Phase 4)**: depende da US1 (precisa de itens para mostrar) — os testes podem ser escritos
  em paralelo à US1.
- **US3 (Phase 5)**: depende só da Foundational; independente de US1/US2, exceto o trecho do
  `SecretFriendCard` (valor junto da lista), que vem depois da T030.
- **Polish (Phase 6)**: depende de todas as histórias.

```text
Setup ─▶ Foundational ─┬─▶ US1 ─▶ US2 ─┐
                       └─▶ US3 ────────┴─▶ Polish
```

### Within Each User Story

- Testes primeiro, vendo-os **falhar**.
- Migração → entidades e repositórios → serviços → controllers → frontend (api → componentes → páginas).
- Commit ao fim de cada tarefa ou grupo lógico (Conventional Commits).

### Parallel Opportunities

- Setup: T002 em paralelo com T001.
- Foundational: T003 primeiro; depois T005, T006, T007 e T008 em paralelo.
- Em cada história, todas as tarefas de teste [P] podem ser escritas em paralelo.
- US3 pode andar em paralelo com US1/US2 (arquivos diferentes, exceto `SecretFriendCard.tsx`).

---

## Parallel Example: User Story 1

```bash
# Testes primeiro, em paralelo:
Task: "T009 WishItemUrlTest"
Task: "T010 WishlistServiceTest"
Task: "T011 GroupServiceTest (myWishlist)"
Task: "T012 WishlistControllerWebTest"
Task: "T013 WishlistFlowIT (US1)"
Task: "T014 WishlistEditor.test.tsx"

# Depois, peças independentes em paralelo:
Task: "T015 WishItemUrl"
Task: "T016 DTOs do item"
Task: "T020 api/wishlist.ts + zod"
```

---

## Implementation Strategy

### MVP

**MVP = Setup + Foundational + US1 + US2** (as duas histórias P1): sem a US2, a lista não chega a
quem compra o presente. A US3 (P2) é um incremento.

1. Setup → Foundational → migração V7 validada.
2. US1 → **parar e validar** (montar a lista, ninguém mais a vê).
3. US2 → **parar e validar** (revelar mostra a lista certa; sigilo) → **MVP pronto**.
4. US3 → validar (valor do presente) → Polish → PR para a `main`.

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
- [USn] mapeia a tarefa para a história da [spec.md](./spec.md).
- Todo teste deve falhar antes da implementação correspondente.
- Evitar: tarefas vagas, dois agentes no mesmo arquivo ao mesmo tempo e qualquer atalho que exponha
  a lista de alguém para quem não o tirou.
