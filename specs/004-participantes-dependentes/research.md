# Research: Participantes Dependentes

Decisões técnicas da feature 004. A numeração continua a das features anteriores
(001: R1–R12; 002: R13–R25; 003: R26–R32). Nenhum item do Technical Context ficou como
*NEEDS CLARIFICATION*: a stack, o banco, os testes e a entrega são os mesmos, e a política do
projeto proíbe dependências novas.

---

## R33. Como representar o dependente

**Decision**: o dependente é uma linha de `app_user` do tipo `DEPENDENT`, sem e-mail e sem senha,
e uma participação (`group_member`) com `guardian_id` apontando para a participação do responsável
no mesmo grupo.

- `app_user` ganha `kind` (`ACCOUNT` | `DEPENDENT`, padrão `ACCOUNT`); `email` e `password_hash`
  passam a aceitar nulo, com uma `CHECK` amarrando: conta ⇔ e-mail e senha preenchidos; dependente
  ⇔ os dois nulos (FR-002). A `UNIQUE (email)` continua valendo (o PostgreSQL aceita vários nulos).
- `group_member` ganha `guardian_id uuid NULL`, com FK composta
  `(group_id, guardian_id) → group_member (group_id, user_id) ON DELETE CASCADE` e
  `CHECK (guardian_id IS NULL OR guardian_id <> user_id)`. A FK garante, no banco, que o responsável
  participa do mesmo grupo; o `CASCADE` garante que os dependentes saem junto com ele (FR-016).
- Cada `app_user` dependente tem **uma única** participação, criada junto com ele (FR-008): não há
  rota que adicione um dependente existente a outro grupo. O nome fica em `app_user.name`, que já
  tem 80 caracteres (FR-001).
- Quando a participação do dependente acaba, o serviço apaga também a linha de `app_user` — salvo
  se um pedido de saída do próprio dependente a referencia (R37), caso em que ela fica só para o
  histórico e para a regra de não readicionar.

**Rationale**: toda a modelagem das features 001–003 é por `user_id`: `group_member`,
`draw_assignment` (com o par cifrado com AAD `group_id‖giver_id`), `wish_item`, `exit_request`,
`group_exit_ban`. Com o dependente como um `app_user`, o sorteio, a cifra, a lista de desejos, os
pedidos de saída e as contagens funcionam sem bifurcar o código nem as FKs. Ele nunca entra no
sistema: o login busca por e-mail e o dependente não tem e-mail nem senha; nenhuma sessão nasce
para ele.

**Alternatives considered**:
- Tabela `dependent` separada e participação com id substituto — exigiria trocar a chave de
  `group_member` e de todas as FKs que a usam (sorteio, lista, saída), mexendo em código já
  testado de sigilo. Muito mais risco para o mesmo resultado.
- Guardar o responsável em `app_user.guardian_id` — não garantiria no banco que o responsável está
  no mesmo grupo, nem tiraria os dependentes junto com ele.
- Dependente sem participação (só uma lista dentro da participação do responsável) — o sorteio
  teria de tratar "meia pessoa", e o par cifrado não teria um `giver_id` próprio.

---

## R34. Adicionar, renomear e remover (FR-001 a FR-007)

**Decision**: rotas sob `/groups/{groupId}/my-dependents`, sempre com o usuário da sessão como
responsável, dentro do `GroupLock` (mesmo padrão das demais escritas).

- **Adicionar** (`POST`): só em `OPEN` (`409 group-already-drawn` ou `409 group-finished`, como quem tenta entrar depois do
  sorteio); só para participante **com conta** (um dependente não tem sessão, então isso vem da
  própria sessão); grupo cheio conta os dependentes (`409 group-full`, FR-004); no máximo 5 por
  responsável e grupo (`409 dependents-limit`, FR-003); bloqueado depois de uma saída aprovada de
  dependente daquele responsável (`409 dependents-locked`, R37). Nome com 2 a 80 caracteres, sem
  espaços nas pontas (Bean Validation, FR-001). A confirmação nasce igual à do responsável (R36).
- **Renomear** (`PUT /{dependentId}`) e **remover** (`DELETE /{dependentId}`): só em `OPEN`; o
  dependente é buscado por `(dependentId, groupId, guardian_id = usuário da sessão)`; qualquer outro
  caso responde `404` igual (dependente de outra pessoa, de outro grupo ou inexistente), sem dizer
  se existe (FR-007).
- **O organizador remove** pelo `DELETE /groups/{groupId}/participants/{userId}` que já existe
  (FR-006): a regra é a mesma de remover qualquer participante. Remover um participante com conta
  remove também os dependentes dele (FR-016).
- **Sair do grupo** antes do sorteio (`DELETE /participants/me`) tira os dependentes junto.
- Toda mudança publica `participants-changed` depois do commit (sse-events.md).

**Rationale**: "`my-dependents`" deixa explícito que a rota só alcança os dependentes do usuário
da sessão, como o `mine` da lista de desejos. O `404` uniforme repete a proteção contra IDOR das
features anteriores.

**Alternatives considered**:
- `POST /groups/{groupId}/participants` com `{name, dependent: true}` — misturaria a entrada por
  código (participante com conta) com um cadastro feito por outra pessoa, com regras diferentes.
- Deixar o organizador renomear — a spec só dá a ele a remoção (FR-006, FR-007).

---

## R35. Sorteio com restrições (FR-009, FR-009a, FR-009b; Constituição II)

**Decision**: o `DerangementGenerator` passa a receber os **pares proibidos** além dos
participantes, e o sorteio continua sendo **rejeição sobre embaralhamentos uniformes**, precedida
de uma **verificação de viabilidade** por emparelhamento bipartido.

- **Pares proibidos**: para cada dependente `d` com responsável `r`, `r → d` e `d → r`. Irmãos
  (dependentes do mesmo responsável) **podem** se tirar (Edge Case). Ninguém tira a si mesmo, como
  antes.
- **Viabilidade**: existe atribuição válida ⇔ o grafo bipartido "quem dá → quem pode receber" tem
  um emparelhamento perfeito. Verificado com o algoritmo de caminhos aumentantes (Kuhn), O(n·m),
  trivial para n ≤ 100. Sem emparelhamento perfeito, o sorteio é recusado **antes de qualquer
  escrita** com `422 no-valid-draw` e a mensagem da spec (FR-009b).
- **Uniformidade**: cada tentativa é um Fisher–Yates com `SecureRandom` (uniforme sobre todas as
  permutações); descartar inteiras as que violam qualquer regra dá distribuição **uniforme sobre as
  atribuições válidas**, como a constituição exige. Nenhuma "correção" local do resultado (trocar
  um par inválido) é feita, porque isso tiraria a uniformidade.
- **Tentativas**: com dependentes, a fração de permutações válidas pode ser bem menor que 1/e.
  O pior caso realista é um grupo pequeno dominado por uma família: responsável, 5 dependentes e
  mais 1 pessoa (7 participantes) têm 44 atribuições válidas em 5.040 (~0,9%). O limite passa de
  1.000 para **200.000** tentativas — esgotar esse limite num grupo viável tem probabilidade
  desprezível (< 10⁻⁷⁰⁰ no pior caso acima), e cada tentativa custa microssegundos. Esgotar o limite
  com o grupo viável continua sendo `IllegalStateException` (erro interno, nada persistido).
- **Motivo visível antes**: `DrawBlockedReason` ganha `NO_VALID_DRAW` (`no-valid-draw`), verificado
  logo depois de `NOT_ENOUGH_PARTICIPANTS` e antes de `PARTICIPANTS_PENDING` — é um bloqueio que só
  se resolve chamando mais gente. O `GroupDetail.drawBlockedReason` já leva o código para a tela
  (FR-009b).

**Rationale**: rejeição é o mesmo método já aprovado (R6) e é a forma mais simples de provar
uniformidade. A verificação de viabilidade transforma o "sorteio impossível" num bloqueio
determinístico, em vez de esperar o limite de tentativas acabar.

**Alternatives considered**:
- Gerar direto uma atribuição válida por backtracking aleatório — rápido, mas **não** uniforme.
- Cadeia de Markov (trocas sucessivas) — só aproximadamente uniforme e muito mais difícil de testar.
- Recusar só depois de esgotar as tentativas — lento, não diz o motivo antes e confunde "azar" com
  "impossível".

---

## R36. Confirmação do dependente (FR-013)

**Decision**: a confirmação do dependente é **espelho** da do responsável, gravada em
`group_member.confirmed_at` das participações dos dependentes, no mesmo `GroupLock` e na mesma
transação de cada mudança da do responsável.

- Responsável confirma ou volta a pendente → os dependentes dele recebem o mesmo `confirmed_at`.
- Dependente adicionado → nasce com o `confirmed_at` do responsável.
- Reset por mudança de data, horário ou local (feature 002) → os dependentes voltam a pendente
  junto com o responsável; os dependentes do **organizador**, que continua confirmado, continuam
  confirmados.
- `POST /confirmation` continua valendo só para o usuário da sessão; não existe rota para
  confirmar um dependente separadamente.
- O motivo `participants-pending` lista só **participantes com conta** ("Falta 1 confirmar: Ana."),
  porque é a Ana quem precisa agir; a contagem de confirmados (`confirmedCount`) conta todos.

**Rationale**: as consultas de pendência e a contagem da 002 já leem `confirmed_at`; gravar o
espelho mantém essas consultas intactas. Derivar a confirmação na leitura exigiria reescrever cada
uma delas.

**Alternatives considered**:
- Confirmação calculada a partir do responsável na leitura — uma única fonte de verdade, mas
  espalha um `JOIN` por todas as consultas da 002 e muda a semântica da coluna.
- Confirmar cada dependente separadamente — a spec diz que a confirmação do responsável vale para
  os dependentes.

---

## R37. Pedido de saída do dependente e "não readicionar" (FR-015, FR-016)

**Decision**: `exit_request` ganha `requested_by` (quem pediu: o próprio usuário ou o responsável),
e o pedido de saída do dependente usa o mesmo fluxo da 002.

- `requested_by uuid NOT NULL REFERENCES app_user (id)`, preenchido com `user_id` nos pedidos já
  existentes. Os avisos de decisão (`exit-request-notices`) passam a ser buscados por
  `requested_by`, e o "visto" (`ack`) também é aceito só de quem pediu: quem recebe e dispensa
  "Saída aprovada/recusada" do Lucas é a Ana. O índice
  `ix_exit_request_unseen` passa a ser sobre `requested_by`.
- **Pedir** e **cancelar**: `POST` e `DELETE /groups/{groupId}/my-dependents/{dependentId}/exit-request`,
  com o dependente buscado como em R34. Mesmas regras da 002 (só `DRAWN`, um pendente por pessoa).
  **Aprovar/recusar** continua nas rotas do organizador, por id do pedido.
- **Aprovação** do pedido do dependente: igual à 002 — desfaz o sorteio, tira a participação, cria
  o `group_exit_ban` do dependente. A linha de `app_user` do dependente fica (o pedido a referencia).
- **Não readicionar** (Clarifications Q3): ao adicionar, verifica se existe pedido `APPROVED` no
  grupo com `requested_by` = usuário da sessão e `user_id` ≠ usuário da sessão; se existir,
  `409 dependents-locked` ("Depois da saída de um dependente seu, não dá para adicionar outros
  neste grupo."). Os demais participantes continuam adicionando os seus.
- **Saída aprovada do responsável**: os dependentes saem juntos (FK com `CASCADE` em R33), na
  mesma operação que desfaz o sorteio; só o responsável ganha `group_exit_ban` (os dependentes não
  têm identidade fora do grupo, e o `app_user` deles é apagado); pedidos pendentes desses
  dependentes são cancelados na mesma transação. Um único `draw-undone` é publicado.

**Rationale**: um pedido por pessoa (o dependente) mantém a tela do organizador e o desfazer do
sorteio iguais aos da 002; só o destinatário do aviso muda. O dependente não tem identidade fora do
grupo, então a proibição de voltar precisa recair sobre o responsável (Princípio II).

**Alternatives considered**:
- `group_exit_ban` do responsável — o impediria de **voltar ao grupo**, o que a spec não pede.
- Coluna `group_member.dependents_locked` — mais uma coluna para um fato que o histórico de
  pedidos já registra.

---

## R38. Resultado dos dependentes (FR-010 a FR-012; Constituição I)

**Decision**: rota nova `GET /groups/{groupId}/my-dependents/secret-friends`, que **não recebe
nenhum id**: devolve, para cada dependente do usuário da sessão naquele grupo, o nome do
dependente e o resultado dele no mesmo formato de `my-secret-friend` (nome, lista de desejos,
`wishlistUpdatedAt`).

- O conjunto de dependentes vem de `group_member.guardian_id = usuário da sessão`; para cada um, o
  `SecretFriendService` decifra o par com `giver_id` = id do dependente, exatamente como faz para o
  próprio usuário (R27, R9). Nenhum id de quem foi tirado sai na resposta.
- Mesmas regras de situação: `DRAWN` ou `FINISHED`; senão `409 group-not-drawn`. Sem dependentes,
  `200` com lista vazia (não revela nada). `Cache-Control: no-store`.
- Na tela, cada cartão "Amigo secreto do Lucas" tem o próprio Revelar/Ocultar: o primeiro "Revelar"
  busca a lista (TanStack Query, `gcTime: 0`) e só o cartão revelado mostra o resultado; ao ocultar
  o último, a consulta é descartada. Nada vai para armazenamento do navegador (FR-010).
- O organizador não ganha nenhum caminho novo: `pendingExitRequests`, `participants` e os eventos
  SSE continuam sem pares.

**Rationale**: a constituição v1.3.0 exige que os resultados de dependentes visíveis sejam
derivados do usuário autenticado, **nunca de um identificador enviado pelo cliente**. Uma rota
`/my-dependents/{id}/secret-friend` usaria o id do cliente para escolher o resultado — mesmo
filtrado pelo responsável, seria exatamente o padrão que a constituição proíbe. Sem id na rota,
não há o que adulterar.

**Alternatives considered**:
- Um resultado por dependente com id na rota — mais econômico por cartão, mas contraria a letra do
  Princípio I.
- Incluir os resultados dos dependentes em `my-secret-friend` — mudaria o formato de uma rota já
  testada de sigilo e revelaria tudo junto, contra o Revelar separado (FR-010).

---

## R39. Lista de desejos do dependente (FR-014)

**Decision**: rotas `POST/PUT/DELETE /groups/{groupId}/my-dependents/{dependentId}/wishlist/items[/{itemId}]`,
com as mesmas regras da 003 (5 itens, link validado, só antes de `FINISHED`). O `WishlistService`
passa a receber o **dono** da lista já resolvido: o usuário da sessão (rotas `mine`) ou um
dependente dele (busca de R34, `404` uniforme).

- Os itens do dependente ficam em `wish_item` com `user_id` = id do dependente; o `ON DELETE
  CASCADE` da 003 os apaga quando a participação acaba.
- Quem tirou o dependente vê a lista pelo `my-secret-friend` de sempre (R27), sem mudança.
- `GroupDetail.myDependents[].wishlist` traz a lista de cada dependente para o responsável editar
  (mesmo schema `MyWishlist`).

**Rationale**: reaproveita toda a regra e a validação da 003; só a resolução do dono muda.

**Alternatives considered**: `wishlist/{userId}` genérico para qualquer dono — seria um IDOR por
construção e misturaria a lista própria com a de terceiros.

---

## R40. O que a API passa a mostrar (FR-005, FR-011)

**Decision**: mudanças aditivas nos schemas.

- `Participant` ganha `dependent: boolean` e `guardianName: string | null` — a lista mostra
  "Lucas (dependente de Ana)" (FR-005). `userId` continua vindo (é o que o organizador usa para
  remover).
- `GroupDetail` ganha `myDependents: MyDependent[]` (só os do usuário da sessão: id, nome, lista de
  desejos e pedido de saída pendente), `maxDependents: 5` e `canAddDependents` +
  `dependentsBlockedReason` (`group-already-drawn`, `group-finished`, `group-full`, `dependents-limit`,
  `dependents-locked` ou nulo) para a tela explicar antes de tentar.
- `ExitRequest` ganha `guardianName` (nulo para conta): o organizador vê "Lucas (dependente de
  Ana)" no pedido.
- `ExitRequestNotice` ganha `subjectName` (nome do dependente; nulo quando o pedido foi do próprio
  usuário): a Ana vê "Lucas saiu do grupo X".

**Rationale**: o cliente já lê esses schemas; campos novos não quebram as telas da 001–003.

---

## R41. Interface

**Decision**: nenhuma tela nova; a página do grupo ganha um cartão "Meus dependentes" (adicionar,
renomear, remover, lista de desejos, pedir saída) e um cartão "Amigo secreto do {nome}" por
dependente, logo abaixo do "Seu amigo secreto". Detalhes em [ui-routes.md](./contracts/ui-routes.md).

- Componentes novos: `DependentsCard`, `DependentForm` (zod: nome 2–80), `DependentSecretFriendCard`.
- `ParticipantList` mostra "(dependente de Ana)" em texto secundário.
- `WishlistEditor` e o fluxo de pedido de saída são reaproveitados com o dono como parâmetro.
- Mensagens dos bloqueios (`dependents-limit`, `dependents-locked`, `no-valid-draw`) em pt-BR.

---

## R42. Estratégia de testes (Constituição IV)

**Decision**: teste primeiro nas regras novas, com os mesmos níveis da 003.

- **Sorteio (propriedade)**: para grupos aleatórios de 3 a 100 participantes com responsáveis e até
  5 dependentes cada — nenhum auto-sorteio, bijeção completa, nenhum par responsável↔dependente;
  para **todas** as configurações pequenas (n ≤ 7, enumeradas), viabilidade calculada ==
  existência de atribuição válida por força bruta; grupos inviáveis sempre recusados.
- **Uniformidade**: teste estatístico (qui-quadrado) num grupo pequeno com restrições, comparando
  a frequência de cada atribuição válida.
- **Sigilo** (`SecrecyIT` ampliado, SC-002): outro participante, o organizador e quem foi tirado
  pelo dependente não obtêm o resultado do dependente por nenhuma rota; `my-dependents/secret-friends`
  de quem não é responsável vem vazio; nenhuma resposta, erro, aviso ou evento contém o par.
- **Autorização** (SC-004): renomear, remover, lista e pedido de saída de dependente alheio → `404`.
- **Integração (Testcontainers)**: migração V8 com dados antigos; ciclo adicionar → confirmar →
  sortear → revelar → pedir saída → aprovar → `dependents-locked`; saída do responsável levando os
  dependentes; cascata da lista.
- **Login**: tentativa de login com nome de dependente/sem e-mail nunca autentica.
- **Contrato**: `OpenApiContractIT` passa a comparar a união 001 + 002 + 003 + 004 (modo parcial
  durante as entregas por história, como na 003).
- **Frontend**: Vitest + RTL + MSW nos componentes novos; gate de 80% mantido.
