# Research: Detalhes do Evento, Confirmação de Participação e Grupo Finalizado

**Feature**: `002-grupo-evento-confirmacao` | **Data**: 2026-11-18 | **Plan**: [plan.md](./plan.md)

Continua as decisões R1–R12 da [feature 001](../001-amigo-secreto-sorteio/research.md), que
seguem valendo. A numeração continua (R13 em diante) para que as referências não se confundam.
Nenhuma dependência nova é necessária: tudo usa o que a feature 001 já tem (Spring Boot,
`@Scheduled`, Flyway, SSE, TanStack Query, react-hook-form + zod, `Intl` do navegador).

---

## R13. Data e horário do evento sem violar "datas em UTC"

**Decision**: o banco guarda **instantes em UTC**, como manda a constituição:

- `event_starts_at timestamptz`: data e horário informados, interpretados no horário de
  Brasília (`America/Sao_Paulo`). Sem horário, é a meia-noite (horário de Brasília) do dia.
- `event_has_time boolean`: diz se o horário foi informado, para exibir só a data quando não foi.
- `finalizes_at timestamptz`: quando o grupo deve finalizar (FR-017) — igual a
  `event_starts_at` se há horário; senão, a meia-noite do dia seguinte em Brasília (fim do dia).

A API fala a língua do usuário: `eventDate` (`AAAA-MM-DD`) e `eventTime` (`HH:mm` ou `null`),
sempre em horário de Brasília. A conversão fica numa classe pura, `EventSchedule`, testável sem
Spring, com `ZoneId` fixo `America/Sao_Paulo` e o `Clock` UTC já existente.

**Rationale**: um `date` + `time` local seria "data sem fuso" e contrariaria a regra de UTC.
Guardar `finalizes_at` pronto deixa a busca de grupos a finalizar simples e indexável. O fuso é
fixo porque a spec define horário de Brasília para todos.

**Alternatives considered**:
- Colunas `date` e `time` locais — mais naturais, mas fogem da regra de UTC e obrigam a
  converter em toda consulta.
- Só `event_starts_at` e calcular o fim do dia na consulta — exige lógica de fuso no SQL.

---

## R14. "Hoje ou no futuro" e horário já passado

**Decision**: "hoje" é a data atual **em Brasília**, calculada pelo `EventSchedule` a partir do
`Clock`. Regras ao informar ou alterar a data (FR-003):

- data anterior a hoje → recusada (`422`, "A data do evento precisa ser hoje ou no futuro.");
- data de hoje com horário informado já passado → recusada ("Esse horário já passou.");
- a validação só roda quando data ou horário **mudam** (FR-003: "no momento em que é informada
  ou alterada"); editar só a descrição ou o local de um grupo com data vencida é permitido.

O frontend repete as regras com zod para dar o erro na hora, mas quem decide é o servidor.

**Rationale**: evita criar um grupo que já nasceria "acontecido" e evita que o fuso do
navegador mude o resultado.

---

## R15. Confirmação ligada à versão dos detalhes (FR-015, FR-035)

**Decision**:

- `group_member.confirmed_at timestamptz NULL`: `NULL` = *pendente*; preenchido = *confirmado*.
- `secret_group.details_version int`: começa em 1 e aumenta em 1 sempre que data, horário ou
  local mudam (a descrição não conta — FR-015).
- O `GroupDetail` devolve `detailsVersion`. O cliente confirma com
  `POST /groups/{id}/confirmation` enviando `{"detailsVersion": n}`.
- No servidor, com a linha do grupo travada (`FOR UPDATE`, o mesmo lock de R7):
  1. grupo precisa estar `OPEN`, senão `409`;
  2. se `n` ≠ versão atual → `409 details-changed`, "Os detalhes do evento mudaram. Confira e
     confirme de novo."; o cliente recarrega o grupo e mostra os detalhes novos;
  3. se já confirmado → nada muda, `200` (idempotente — FR-010);
  4. senão grava `confirmed_at`.
- Ao salvar data/horário/local em grupo `OPEN`: incrementa a versão e faz
  `UPDATE group_member SET confirmed_at = NULL WHERE group_id = ? AND user_id <> organizador`,
  na mesma transação.

**Rationale**: o lock na linha do grupo serializa "confirmar" e "alterar detalhes", e a versão
garante que o aceite foi dado sobre o que a pessoa viu, mesmo que a tela dela esteja
desatualizada há minutos (não só na corrida de milissegundos).

**Alternatives considered**:
- Comparar data/local enviados pelo cliente — mais campos para validar e para errar.
- `ETag`/`If-Match` HTTP — mesmo efeito, mas o grupo inteiro mudaria de ETag a cada confirmação
  de outra pessoa, recusando confirmações sem motivo.

---

## R16. Condições do sorteio (FR-013, FR-014)

**Decision**: o `DrawService` ganha, dentro da transação e com o lock já existente, as
verificações novas, nesta ordem (a primeira que falhar responde):

| Ordem | Condição | Resposta |
|-------|----------|----------|
| 1 | grupo sem detalhes do evento (grupo antigo) | `422 event-details-required` |
| 2 | data do evento já passou | `422 event-date-passed` |
| 3 | menos de 3 participantes | `422 not-enough-participants` (já existe) |
| 4 | algum participante pendente | `422 participants-pending`, com os nomes de quem falta em `pending` |

O `GroupDetail` passa a ter `drawBlockedReason` (o mesmo código, ou `null`) para o botão dizer
por que está bloqueado sem precisar tentar (FR-014).

**Rationale**: como o lock é o mesmo da entrada no grupo, a confirmação e a alteração de
detalhes, "alguém entrou no exato momento do sorteio" (Edge Case) é resolvido sem nada novo.
Os nomes dos pendentes já são visíveis para todos os membros, então não há vazamento.

---

## R17. Finalização automática (FR-017, FR-021, FR-033)

**Decision**: duas camadas, ambas idempotentes:

1. **Tarefa agendada** `GroupFinalizer` (`@Scheduled(fixedDelay = 60 s)`, já há
   `@EnableScheduling`): busca grupos `DRAWN` com `finalizes_at <= agora`
   (`FOR UPDATE SKIP LOCKED`, até 100 por vez) e, para cada um, numa transação: muda para
   `FINISHED`, grava `finished_at`, cancela pedidos de saída pendentes e publica o evento
   `group-finished` após o commit. Roda também na subida da aplicação, recuperando o que
   venceu enquanto ela estava parada.
2. **Verificação na escrita**: toda operação que trava o grupo chama antes
   `group.finishIfDue(now)`. Assim, entre dois ciclos da tarefa, ninguém consegue confirmar,
   pedir saída ou aprovar saída num grupo cujo evento já aconteceu.

A leitura (`GET /groups/{id}`) não muda o estado; um grupo vencido pode aparecer como
`DRAWN` por até ~1 minuto, o que cabe no "sem recarregar" da FR-021.

**Rationale**: sem dependência nova (ShedLock, Quartz): a constituição fixa uma instância só, e
o `SKIP LOCKED` já protege se um dia houver duas.

**Alternatives considered**:
- Só calcular o status na leitura (status "virtual") — complica consultas, a tela inicial e o
  cancelamento dos pedidos pendentes.
- Agendamento por grupo (`TaskScheduler.schedule` no horário exato) — perde-se ao reiniciar.

---

## R18. Pedido de saída e "desfazer o sorteio" (FR-025 a FR-033)

**Decision**:

- Tabela `exit_request` com situação `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` e índice
  único parcial `(group_id, user_id) WHERE status = 'PENDING'`: no máximo um pendente por
  pessoa (FR-025), garantido também pelo banco. Recusado ou cancelado, pode pedir de novo.
- Novo pacote `exitrequest` (controller → service → repository), seguindo a organização por
  feature.
- **Aprovar** é uma transação só, com a linha do grupo travada:
  1. `finishIfDue`; grupo `DRAWN`; usuário da sessão é o organizador; pedido `PENDING`;
  2. `DrawService.discardDraw(group)` apaga **todas** as linhas de `draw_assignment` do grupo
     (`DELETE` em massa, sem ler nenhum par) e volta o grupo para `OPEN`, `drawn_at = NULL` e
     `draw_undone_at = agora` (para avisar quem não estava online);
  3. apaga a participação de quem saiu e grava `group_exit_ban` (R19);
  4. pedido → `APPROVED`; demais pendentes → `CANCELLED` (Edge Case);
  5. após o commit: `membership-revoked` (motivo `exit-approved`) para quem saiu,
     `draw-undone` e `participants-changed` para os demais.
- Confirmações de quem ficou continuam valendo (FR-028); quem entrar depois começa pendente.
- O novo sorteio é o mesmo `DrawService.draw` de sempre, com `SecureRandom` novo: independente
  do anterior (FR-029). Duas abas aprovando e sorteando ao mesmo tempo são serializadas pelo
  lock: o sorteio só encontra `OPEN` depois do commit da aprovação.
- O organizador não pode pedir saída (`409 organizer-cannot-leave`) nem remover alguém depois do
  sorteio (já é assim na 001 — FR-031).

**Descarte irrecuperável (Constituição II)**: os pares já são cifrados (R9) e o `DELETE` os tira
do banco na mesma transação; a aplicação não guarda cópia, log nem histórico do sorteio
desfeito, e não existe rota que os leia. Restos físicos fora do controle da aplicação — linhas
mortas até o *autovacuum*, WAL e backups feitos **antes** da aprovação — continuam cifrados e só
seriam legíveis por quem tem a chave e o backup. Isso é documentado no quickstart como cuidado
operacional (retenção de backups).

**Alternatives considered**:
- *Crypto-shredding* (chave por sorteio, apagada ao desfazer) — a chave apagada também ficaria
  nos mesmos backups e WAL; mais complexidade sem ganho real.
- Marcar os pares como "descartados" em vez de apagar — contraria "descartados de forma
  definitiva".

---

## R19. Impedir a volta de quem saiu depois do sorteio (FR-030)

**Decision**: tabela `group_exit_ban (group_id, user_id, created_at)`, PK composta, gravada na
aprovação. `GroupService.join` verifica antes de inserir: `409 rejoin-not-allowed`, "Você saiu
deste grupo depois do sorteio e não pode entrar de novo." A mensagem só aparece para quem tem o
código, então não revela nada novo.

**Rationale**: a participação é apagada na saída (como na 001), então o registro precisa de
tabela própria. Separar do `exit_request` deixa a regra explícita e a consulta trivial.

---

## R20. Aviso guardado da decisão sobre o pedido (FR-026)

**Decision**: `exit_request.decision_seen_at timestamptz NULL`. Duas rotas, sempre do usuário da
sessão:

- `GET /api/v1/exit-request-notices` → decisões ainda não vistas (`APPROVED`, `REJECTED`,
  `CANCELLED`), com `groupId`, `groupName` e `status`. A tela inicial mostra as aprovadas
  ("Você saiu do grupo X"); a tela do grupo mostra as recusadas/canceladas daquele grupo.
- `POST /api/v1/exit-request-notices/{id}/ack` → marca como visto (idempotente; `404` se o pedido
  não é da pessoa).

Quem está online recebe também o sinal SSE e vê o aviso na hora; o registro garante que quem
estava fora veja na próxima entrada.

**Rationale**: o grupo some para quem teve a saída aprovada, então o aviso não pode depender da
tela do grupo. Sem e-mail nem push, como a spec pede.

---

## R21. Eventos em tempo real novos

**Decision**: continuam sendo **sinais sem dados sensíveis** (R8). Novos eventos:

| Evento | Quando |
|--------|--------|
| `group-updated` | detalhes alterados, alguém confirmou, pedido de saída criado/cancelado/recusado |
| `draw-undone` | saída aprovada: sorteio desfeito |
| `group-finished` | grupo finalizado |

`group-updated` leva `confirmationsReset: true` quando a alteração voltou as confirmações a
pendente, para o cliente mostrar "Os detalhes mudaram, confirme de novo". `membership-revoked`
ganha o campo `reason` (`removed` / `exit-approved`); a mudança é aditiva.

**Rationale**: um sinal genérico (`group-updated`) evita multiplicar eventos que o cliente trata
igual (refazer a consulta). `draw-undone` e `group-finished` existem à parte porque mudam a tela
de forma especial (esconder o nome revelado, mudar a seção na tela inicial).

---

## R22. Grupos criados antes desta feature (FR-034)

**Decision**: a migração não inventa dados:

- detalhes do evento ficam `NULL` (a restrição `ck_secret_group_event` exige "tudo ou nada");
- `details_version = 1`;
- `confirmed_at`: o organizador recebe `joined_at`; nos grupos `DRAWN` todos recebem `drawn_at`
  (a confirmação não importa depois do sorteio — FR-016); nos grupos `OPEN` os demais ficam
  pendentes (Edge Case da spec).

O `GroupDetail` expõe `eventDetailsMissing: true`, e a tela do organizador mostra o aviso
"Informe a data e o local do evento". Enquanto não houver data, `finalizes_at` é `NULL` e a
tarefa de R17 ignora o grupo.

**Rationale**: atende o clarify (grupos antigos esperam o organizador) sem chutar datas.

---

## R23. Interface

**Decision**:

- Campos nativos `<input type="date">` e `<input type="time">` (teclado e seletor do celular,
  sem biblioteca de calendário); formatação com `Intl.DateTimeFormat('pt-BR', { timeZone:
  'America/Sao_Paulo' })` em `lib/eventDate.ts`.
- Descrição e local exibidos como texto do React (nunca `dangerouslySetInnerHTML`), com
  `whitespace-pre-line` para manter as quebras de linha (FR-007).
- Novos componentes em `components/group/`: `EventDetailsCard`, `EventDetailsForm` (criar e
  editar), `ConfirmParticipationCard`, `ExitRequestPanel` (participante) e `ExitRequestsList`
  (organizador). `ParticipantList` ganha o selo *Confirmado*/*Pendente* e o contador.
- A tela inicial separa "Em andamento" e "Finalizados" e mostra os avisos de saída aprovada.

**Rationale**: nada de dependência nova; os controles nativos são os mais acessíveis no
celular (Constituição VI).

---

## R24. Contrato OpenAPI em duas partes

**Decision**: o [contrato desta feature](./contracts/openapi.yaml) lista só o que é novo ou
mudou. O `OpenApiContractIT` passa a ler os dois arquivos (001 e 002) e comparar a **união** das
rotas com as geradas pelo springdoc. Rotas alteradas aparecem nos dois arquivos; a união
elimina a repetição.

**Rationale**: mantém a spec 001 como registro do que foi entregue e deixa a revisão da 002
focada no que muda, sem perder a verificação automática de que a API e o contrato batem.

**Alternatives considered**: copiar o contrato inteiro para a 002 — duplicaria ~440 linhas e
as duas cópias se desencontrariam.

---

## R25. Testes (Constituição IV)

**Decision**: mesma pirâmide da R11, com teste primeiro nas regras críticas:

| Camada | O que cobre |
|--------|-------------|
| Unitário puro | `EventSchedule` (sem horário = fim do dia; horário; "hoje" em Brasília perto da meia-noite UTC), `SecretGroup` (transições, `finishIfDue`, `drawBlockedReason`, `undoDraw`) |
| Serviço (Mockito) | confirmação (versão, idempotência, grupo errado), alteração de detalhes (reabre ou não), pedido de saída (todas as recusas), aprovação (ordem dos passos e eventos) |
| `@WebMvcTest` | validação dos corpos novos, CSRF, `403`/`404`/`409`/`422` com `type` correto |
| Integração (Testcontainers) | migração V5/V6 sobre dados da 001 (R22); corrida confirmar × alterar detalhes; corrida entrar × sortear com pendente; aprovar × sortear em paralelo; sigilo após desfazer (nenhuma linha antiga em `draw_assignment`, `my-secret-friend` → `409` até o novo sorteio, novo sorteio só com quem ficou — SC-008); bloqueio de reentrada; `GroupFinalizer` com `Clock` controlado |
| Frontend (Vitest + RTL + MSW) | formulário de detalhes (validação e fuso), confirmação com `details-changed`, motivos do botão de sortear, pedidos de saída (participante e organizador), `draw-undone` apagando o nome revelado, seções da tela inicial e avisos |

Cobertura continua ≥ 80% (JaCoCo e Vitest).
