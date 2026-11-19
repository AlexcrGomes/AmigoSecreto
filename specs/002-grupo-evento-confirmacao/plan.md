# Implementation Plan: Detalhes do Evento, Confirmação de Participação e Grupo Finalizado

**Branch**: `002-grupo-evento-confirmacao` | **Date**: 2026-11-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-grupo-evento-confirmacao/spec.md`

## Summary

O grupo passa a ter descrição, data (com horário opcional) e local do evento, obrigatórios na
criação e editáveis pelo organizador. Cada participante confirma a participação; o sorteio só é
liberado com todos confirmados, pelo menos 3 participantes e a data ainda por vir. Mudar data,
horário ou local antes do sorteio volta todos (menos o organizador) a pendente, e a confirmação
vale só para a versão dos detalhes que a pessoa viu. Depois do evento, o grupo vira
*finalizado* (somente leitura). Depois do sorteio, um participante pode pedir para sair; se o
organizador aprovar, a pessoa sai sem poder voltar, todos os pares são apagados e o grupo volta
a aguardar sorteio.

Abordagem técnica: evolução do monólito da 001, **sem dependências novas**. Duas migrações
Flyway (V5, V6); datas do evento guardadas como instantes UTC e interpretadas em horário de
Brasília por uma classe pura (`EventSchedule`); versão dos detalhes para a confirmação; mesmo
lock pessimista da linha do grupo para serializar entrar, confirmar, editar, sortear e aprovar
saída; tarefa `@Scheduled` + verificação na escrita para finalizar; novos sinais SSE.
Decisões em [research.md](./research.md) (R13–R25).

## Technical Context

**Language/Version**: Java 25 LTS (backend); TypeScript 6.0 (frontend) — sem mudança

**Primary Dependencies**: as da feature 001 (Spring Boot 4.1.2, Spring Security 7, Data JPA,
Flyway, Spring Session JDBC, Bucket4j; React 19.3, Vite 8.3, Tailwind 4.3, React Router,
TanStack Query 5, react-hook-form + zod 4). **Nenhuma dependência nova** e nenhuma atualização
de versão (política de dependências do projeto)

**Storage**: PostgreSQL 18.7 — `secret_group` e `group_member` alteradas; novas `exit_request` e
`group_exit_ban` ([data-model.md](./data-model.md)); migrações `V5`, `V6`

**Testing**: JUnit 5, Mockito, AssertJ, spring-security-test, Testcontainers; Vitest, React
Testing Library, MSW — estratégia em research R25

**Target Platform**: containers Linux (Docker Compose, Docker no WSL); navegadores móveis e
desktop

**Project Type**: aplicação web (backend REST + SPA) — a mesma da 001

**Performance Goals**: mudanças visíveis em ≤ 5 s para quem está com o grupo aberto (SC-004);
finalização em até ~1 min após o horário do evento (R17); aprovar uma saída e sortear de novo
um grupo de 100 em < 1 s cada

**Constraints**: sigilo dos pares (Constituição I) também ao desfazer o sorteio; datas em UTC
e exibidas em horário de Brasília; funcionar a partir de 360px; mudanças de API apenas
aditivas em `/api/v1`

**Scale/Scope**: 3 a 100 participantes por grupo; 1 instância do backend; nenhuma tela nova
(3 telas alteradas); 8 rotas REST novas + 4 alteradas; 3 eventos SSE novos + 1 alterado
([contracts/](./contracts/))

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição **v1.2.1**.

| # | Princípio | Como o plano cumpre | Pré-pesquisa | Pós-design |
|---|-----------|---------------------|:---:|:---:|
| I | Sigilo (não negociável) | Nenhuma rota nova devolve pares; `my-secret-friend` continua a única leitura; pedidos de saída, avisos e SSE sem pares (FR-032); `draw_assignment` ganha só um `DELETE` em massa, nunca leitura do grupo inteiro; teste de integração de sigilo após desfazer (R25) | ✅ | ✅ |
| II | Sorteio justo e simultâneo (não negociável) | Mesmo `DrawService` (SecureRandom, derangement, lock, mínimo de 3) com as condições novas no mesmo lock (R16); desfazer só pela aprovação de saída, numa transação, com todos os pares apagados e sem cópia (R18); quem saiu não volta (R19); nenhuma outra rota desfaz ou refaz. Backups: retenção mínima e separados da chave, como exige a v1.2.1 (quickstart §5) | ✅ | ✅ |
| III | Segurança por padrão | Identidade sempre da sessão (confirmar, pedir saída, avisos); só o organizador edita detalhes e decide pedidos (`403`); `404` para não membros; Bean Validation nos corpos novos; CSRF em todas as escritas; texto do usuário nunca interpretado como HTML (FR-007) | ✅ | ✅ |
| IV | Testes obrigatórios (não negociável) | Teste primeiro em `EventSchedule`, condições do sorteio, desfazer e autorização; Testcontainers para corridas e migração; gate de 80% mantido (R25) | ✅ | ✅ |
| V | Varredura contínua | Sem mudança: o PR passa pelos mesmos CodeQL, Trivy, `npm audit` e Gitleaks | ✅ | ✅ |
| VI | Mobile-first e moderno | Campos nativos de data/hora; selos, avisos e modais nos padrões da 001; estados de carregamento/sucesso/erro; pt-BR ([ui-routes.md](./contracts/ui-routes.md)) | ✅ | ✅ |
| VII | Containerização | Sem mudança nos containers; migrações rodam na subida, como hoje | ✅ | ✅ |
| VIII | Simplicidade | Nenhuma dependência nova; `@Scheduled` em vez de Quartz/ShedLock (uma instância); um sinal SSE genérico em vez de vários; contrato OpenAPI documentado e versionado (R24) | ✅ | ✅ |
| — | Stack obrigatória e `ddl-auto: validate` | Sem mudança | ✅ | ✅ |
| — | Datas em UTC | Instantes em `timestamptz` UTC; o fuso de Brasília só na conversão de entrada/saída (R13) | ✅ | ✅ |

**Resultado**: nenhum desvio. *Complexity Tracking* não se aplica.

**Backups (Princípio II, v1.2.1)**: o descarte vale para o banco do sistema, onde os pares são
apagados na mesma transação. Backups feitos antes da aprovação ainda guardam os pares antigos
(cifrados); a constituição exige retenção mínima e guarda separada da `DRAW_ENCRYPTION_KEY`,
regra operacional descrita no quickstart §5.

**Pendências herdadas, a fechar nesta implementação**:

- README: trocar "o sorteio acontece uma única vez" pela regra da constituição v1.2.0.
- Spec 001 (FR-019 e FR-025): nota apontando que a 002 permite desfazer o sorteio pela saída
  aprovada.

## Project Structure

### Documentation (this feature)

```text
specs/002-grupo-evento-confirmacao/
├── plan.md              # Este arquivo
├── research.md          # Fase 0: decisões R13–R25
├── data-model.md        # Fase 1: tabelas alteradas e novas, estados, migrações
├── quickstart.md        # Fase 1: migração, roteiro manual, sigilo, cuidado com backups
├── contracts/
│   ├── openapi.yaml     # só rotas e schemas novos/alterados (union com o da 001 — R24)
│   ├── sse-events.md    # eventos novos e alterados
│   └── ui-routes.md     # mudanças nas telas
├── checklists/
│   └── requirements.md
└── tasks.md             # Fase 2 (/speckit-tasks — ainda não criado)
```

### Source Code (repository root)

Só o que esta feature cria (➕) ou altera (✏️):

```text
README.md                                   ✏️ regra do sorteio (constituição v1.2.0)
specs/001-amigo-secreto-sorteio/spec.md     ✏️ nota em FR-019/FR-025

backend/src/main/
├── java/br/com/amigosecreto/
│   ├── common/
│   │   └── TimeConfig.java                 ✏️ bean ZoneId America/Sao_Paulo
│   ├── group/
│   │   ├── EventSchedule.java              ➕ classe pura: conversão Brasília ↔ UTC, "hoje", fim do dia (R13, R14)
│   │   ├── EventDetails.java               ➕ valor: descrição, data, horário, local
│   │   ├── SecretGroup.java                ✏️ campos novos, updateEventDetails, undoDraw, finishIfDue, drawBlockedReason
│   │   ├── GroupStatus.java                ✏️ FINISHED
│   │   ├── GroupMember.java                ✏️ confirmedAt, confirm()
│   │   ├── GroupMemberRepository.java      ✏️ reabrir confirmações, contar pendentes, nomes pendentes
│   │   ├── SecretGroupRepository.java      ✏️ grupos a finalizar (FOR UPDATE SKIP LOCKED)
│   │   ├── GroupService.java               ✏️ criar com detalhes, editar detalhes, join com bloqueios novos, GroupDetail novo
│   │   ├── GroupConfirmationService.java   ➕ confirmar com versão (R15)
│   │   ├── GroupMembershipService.java     ✏️ finishIfDue; FINISHED somente leitura
│   │   ├── GroupFinalizer.java             ➕ @Scheduled (R17)
│   │   ├── GroupController.java            ✏️ PUT event-details, POST confirmation
│   │   └── dto/                            ✏️ CreateGroupRequest, GroupDetail, GroupSummary, Participant
│   │                                       ➕ EventDetailsRequest, ConfirmationRequest, EventDetailsResponse
│   ├── draw/
│   │   ├── DrawService.java                ✏️ condições novas (R16); discardDraw (R18)
│   │   ├── DrawAssignmentRepository.java   ✏️ deleteByGroupId (@Modifying)
│   │   └── SecretFriendService.java        ✏️ aceita FINISHED (FR-019)
│   ├── exitrequest/                        ➕ pacote novo (R18–R20)
│   │   ├── ExitRequest.java, ExitRequestStatus.java, ExitRequestRepository.java
│   │   ├── GroupExitBan.java, GroupExitBanRepository.java
│   │   ├── ExitRequestService.java         # pedir, cancelar, aprovar, recusar, cancelar pendentes
│   │   ├── ExitRequestNoticeService.java   # avisos não vistos e "visto"
│   │   ├── ExitRequestController.java
│   │   └── dto/
│   └── events/
│       ├── GroupEvents.java                ✏️ GroupUpdated, DrawUndone, GroupFinished; motivo EXIT_APPROVED
│       └── SseEmitterRegistry.java         ✏️ repassa os sinais novos (R21)
└── resources/db/migration/
    ├── V5__event_details_and_confirmation.sql   ➕
    └── V6__exit_requests.sql                    ➕

backend/src/test/java/br/com/amigosecreto/
├── group/ draw/ exitrequest/               ➕✏️ unitários e de serviço
├── web/                                    ➕✏️ @WebMvcTest das rotas novas
└── integration/                            ➕ migração, corridas, sigilo após desfazer, finalização
                                            ✏️ OpenApiContractIT lê os contratos 001 + 002 (R24)

frontend/src/
├── api/groups.ts                           ✏️ tipos novos; eventDetails, confirm, exitRequests, notices
├── lib/eventDate.ts                        ➕ formatação e "hoje" em Brasília
├── lib/schemas.ts                          ✏️ zod dos detalhes do evento
├── hooks/useGroupEvents.ts                 ✏️ group-updated, draw-undone, group-finished, motivo da revogação
├── hooks/useExitRequestNotices.ts          ➕
├── components/group/
│   ├── EventDetailsCard.tsx                ➕
│   ├── EventDetailsForm.tsx                ➕ criar e editar
│   ├── ConfirmParticipationCard.tsx        ➕
│   ├── ExitRequestPanel.tsx                ➕ participante
│   ├── ExitRequestsList.tsx                ➕ organizador
│   ├── ParticipantList.tsx                 ✏️ selos Confirmado/Pendente e contador
│   ├── DrawButton.tsx                      ✏️ motivo do bloqueio
│   └── SecretFriendCard.tsx                ✏️ limpa o nome em draw-undone
└── pages/
    ├── CreateGroupPage.tsx                 ✏️ campos do evento
    ├── GroupPage.tsx                       ✏️ estados OPEN/DRAWN/FINISHED novos
    └── HomePage.tsx                        ✏️ seções e avisos
```

**Structure Decision**: mesma estrutura da 001 (monorepo `backend/` + `frontend/`, backend por
feature com controller → service → repository). O pedido de saída ganha um pacote próprio
(`exitrequest`) porque tem entidade, regras e rotas próprias. Apagar os pares fica no pacote
`draw` (`DrawService.discardDraw`), para que só ele mexa em `draw_assignment`. As regras de
data e de estado ficam em classes puras (`EventSchedule`, métodos de `SecretGroup`), testáveis
sem Spring.

## Riscos e mitigação

| Risco | Mitigação |
|-------|-----------|
| Desfazer o sorteio deixar par antigo legível pela aplicação | `DELETE` na mesma transação da volta a `OPEN`; `my-secret-friend` exige `DRAWN`/`FINISHED`; teste de integração de sigilo após desfazer (SC-008) |
| Corrida entre confirmar, editar, entrar, sortear e aprovar | todas travam a mesma linha do grupo (`FOR UPDATE`); versão dos detalhes na confirmação; testes de concorrência com Testcontainers |
| Fuso horário (servidor em UTC, usuário em Brasília) | `EventSchedule` com `ZoneId` fixo e `Clock` injetado; testes perto da meia-noite UTC (21h em Brasília) |
| Migração quebrar grupos existentes | V5 preenche só o que é seguro (R22); teste de migração a partir de dados da 001 |
| Finalização atrasar se a aplicação ficar parada | tarefa roda na subida; `finishIfDue` em toda escrita |
| Contrato OpenAPI dividido se desencontrar | `OpenApiContractIT` compara a união dos dois arquivos com a API gerada (R24) |
| Backups guardarem pares de sorteios desfeitos | retenção mínima e guarda separada da chave (constituição v1.2.1, quickstart §5) |

## Complexity Tracking

Não se aplica: o Constitution Check não teve violações.
