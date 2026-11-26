# Implementation Plan: Participantes Dependentes

**Branch**: `004-participantes-dependentes` | **Date**: 2026-11-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-participantes-dependentes/spec.md`

## Summary

Um participante com conta (o responsável) adiciona ao grupo dependentes sem conta — só o nome —,
que entram no sorteio como qualquer participante. Só o responsável vê o resultado de cada
dependente, age por ele (confirmação, lista de desejos, pedido de saída) e o leva junto quando sai.
Responsável e dependente nunca se tiram, e um grupo sem sorteio possível é recusado com o motivo.

Abordagem técnica: evolução do monólito das features 001–003, **sem dependências novas**. O
dependente é uma linha de `app_user` do tipo `DEPENDENT`, sem e-mail nem senha, com uma única
participação ligada à do responsável por FK com `ON DELETE CASCADE` (R33) — assim sorteio, par
cifrado, lista de desejos e pedidos de saída funcionam sem bifurcar. O `DerangementGenerator`
ganha pares proibidos, uma verificação de viabilidade por emparelhamento bipartido e mais
tentativas, mantendo a rejeição uniforme (R35). O resultado dos dependentes sai numa rota sem id,
derivada da sessão (R38). Migração Flyway `V8`. Decisões em [research.md](./research.md) (R33–R42).

## Technical Context

**Language/Version**: Java 25 LTS (backend); TypeScript 6.0 (frontend) — sem mudança

**Primary Dependencies**: as das features 001–003 (Spring Boot 4.1.2, Spring Security 7, Data
JPA, Flyway, Spring Session JDBC, Bucket4j; React 19.3, Vite 8.3, Tailwind 4.3, React Router,
TanStack Query 5, react-hook-form + zod 4). **Nenhuma dependência nova** e nenhuma atualização de
versão (política de dependências do projeto)

**Storage**: PostgreSQL 18.7 — `app_user`, `group_member` e `exit_request` ganham colunas e
restrições ([data-model.md](./data-model.md)); migração `V8`

**Testing**: JUnit 5, Mockito, AssertJ, spring-security-test, Testcontainers; Vitest, React
Testing Library, MSW — estratégia em research R42

**Target Platform**: containers Linux (Docker Compose, Docker no WSL); navegadores móveis e
desktop

**Project Type**: aplicação web (backend REST + SPA) — a mesma das features anteriores

**Performance Goals**: sorteio de 100 participantes com dependentes em < 1 s no servidor
(viabilidade O(n·m) + rejeição); adicionar dependente em < 300 ms no servidor; SC-001 (< 30 s pelo
celular) é de interface

**Constraints**: sigilo (Constituição I) — resultado de dependente só para o responsável, a
partir da sessão, sem id do cliente; uniformidade entre atribuições válidas (Constituição II);
dependente nunca autentica; funcionar a partir de 360px; mudanças de API apenas aditivas em
`/api/v1`

**Scale/Scope**: até 100 participantes por grupo, contando os dependentes; até 5 dependentes por
responsável e grupo; 1 instância do backend; nenhuma tela nova (1 tela alterada); 10 operações REST
novas + 1 alterada; nenhum evento SSE novo ([contracts/](./contracts/))

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição **v1.3.0** (emendada para esta feature em `24d1cae`).

| # | Princípio | Como o plano cumpre | Pré-pesquisa | Pós-design |
|---|-----------|---------------------|:---:|:---:|
| I | Sigilo (não negociável) | Resultado do dependente só em `my-dependents/secret-friends`, **sem id na rota**: o conjunto vem de `guardian_id = usuário da sessão` (R38); nenhum id de quem foi tirado sai; organizador sem caminho novo; dependente sem e-mail/senha nunca tem sessão (R33); rotas de ação por dependente com `404` uniforme (R34); nenhum sinal SSE com pares (sse-events.md); `SecrecyIT` cobre dependentes de terceiros, organizador e quem foi tirado pelo dependente (R42) | ✅ | ✅ |
| II | Sorteio justo, válido e simultâneo (não negociável) | Pares proibidos responsável↔dependente; viabilidade por emparelhamento perfeito antes de qualquer escrita, com `no-valid-draw` (R35); rejeição sobre Fisher–Yates com `SecureRandom` → uniforme entre atribuições válidas; mínimo de 3 contando dependentes (são participações); quem sai depois do sorteio não volta: `group_exit_ban` do dependente e `dependents-locked` para o responsável (R37) | ✅ | ✅ |
| III | Segurança por padrão | Identidade sempre da sessão; só o responsável age pelo dependente (busca por `guardian_id`), além da remoção pelo organizador; Bean Validation no nome; CSRF nas escritas; `CHECK` no banco impede dependente com credenciais | ✅ | ✅ |
| IV | Testes obrigatórios (não negociável) | Propriedade do sorteio com restrições e casos impossíveis (enumeração exaustiva n ≤ 7), teste estatístico de uniformidade, autorização, sigilo, migração e fluxo com Testcontainers; gate de 80% mantido (R42) | ✅ | ✅ |
| V | Varredura contínua | Sem mudança: CodeQL, Trivy, `npm audit` e Gitleaks no PR | ✅ | ✅ |
| VI | Mobile-first e moderno | Cartões e formulários nos padrões existentes; motivos de bloqueio explicados antes da ação; pt-BR ([ui-routes.md](./contracts/ui-routes.md)) | ✅ | ✅ |
| VII | Containerização | Sem mudança nos containers; migração na subida | ✅ | ✅ |
| VIII | Simplicidade | Nenhuma dependência nova; dependente reaproveita `app_user`/`group_member` em vez de modelo paralelo (R33); confirmação por espelho, sem reescrever consultas da 002 (R36); nenhum SSE novo | ✅ | ✅ |
| — | Stack obrigatória e `ddl-auto: validate` | Sem mudança; entidades acompanham a V8 | ✅ | ✅ |
| — | Datas em UTC | Sem coluna de data nova; `confirmed_at` do dependente copia o instante do responsável | ✅ | ✅ |

**Resultado**: nenhum desvio. *Complexity Tracking* não se aplica.

## Project Structure

### Documentation (this feature)

```text
specs/004-participantes-dependentes/
├── plan.md              # Este arquivo
├── research.md          # Fase 0: decisões R33–R42
├── data-model.md        # Fase 1: colunas e restrições novas, quem lê o quê, migração
├── quickstart.md        # Fase 1: migração, roteiro manual, sigilo
├── contracts/
│   ├── openapi.yaml     # só rotas e schemas novos/alterados (união com 001, 002 e 003)
│   ├── sse-events.md    # nenhum evento novo; quais sinais cada ação publica
│   └── ui-routes.md     # mudanças na tela do grupo
├── checklists/
│   └── requirements.md
└── tasks.md             # Fase 2 (/speckit-tasks — ainda não criado)
```

### Source Code (repository root)

Só o que esta feature cria (➕) ou altera (✏️):

```text
backend/src/main/
├── java/br/com/amigosecreto/
│   ├── auth/
│   │   └── AppUser.java                    ✏️ kind, e-mail/senha anuláveis, AppUser.dependent(...)
│   ├── dependent/                          ➕ pacote novo (R34, R38, R39)
│   │   ├── DependentService.java           # adicionar, renomear, remover; limite 5; dependents-locked
│   │   ├── DependentController.java        # /groups/{id}/my-dependents[/{dependentId}]
│   │   ├── DependentLookup.java            # busca (dependentId, groupId, guardian = sessão) → 404 uniforme
│   │   └── dto/                            # DependentRequest, MyDependent, DependentSecretFriend
│   ├── draw/
│   │   ├── DerangementGenerator.java       ✏️ pares proibidos, viabilidade (Kuhn), 200.000 tentativas (R35)
│   │   ├── DrawService.java                ✏️ monta os pares proibidos; no-valid-draw
│   │   ├── SecretFriendService.java        ✏️ resultados dos dependentes da sessão (R38)
│   │   └── DrawController.java             ✏️ GET /my-dependents/secret-friends
│   ├── group/
│   │   ├── GroupMember.java                ✏️ guardianId
│   │   ├── GroupMemberRepository.java      ✏️ dependentes de um responsável; contagem
│   │   ├── DrawBlockedReason.java          ✏️ NO_VALID_DRAW
│   │   ├── GroupConfirmationService.java   ✏️ espelho da confirmação (R36)
│   │   ├── GroupMembershipService.java     ✏️ sair/remover levando os dependentes; apagar o app_user
│   │   ├── GroupService.java               ✏️ GroupDetail com myDependents e bloqueios
│   │   └── dto/                            ✏️ Participant (dependent, guardianName), GroupDetail
│   ├── wishlist/
│   │   ├── WishlistService.java            ✏️ dono resolvido (sessão ou dependente) (R39)
│   │   └── DependentWishlistController.java ➕ /my-dependents/{id}/wishlist/items
│   └── exitrequest/
│       ├── ExitRequest.java                ✏️ requestedBy
│       ├── ExitRequestService.java         ✏️ pedido do dependente; aprovação com dependentes (R37)
│       ├── ExitRequestNoticeService.java   ✏️ avisos por requested_by; subjectName
│       ├── ExitRequestController.java      ✏️ /my-dependents/{id}/exit-request
│       └── dto/                            ✏️ ExitRequestResponse (guardianName), ExitRequestNotice (subjectName)
└── resources/db/migration/
    └── V8__dependents.sql                  ➕

backend/src/test/java/br/com/amigosecreto/
├── draw/                                   ✏️ propriedade com restrições, viabilidade exaustiva, uniformidade
├── dependent/ group/ wishlist/ exitrequest/ ➕✏️ unitários e de serviço
├── web/                                    ➕✏️ @WebMvcTest das rotas novas e alteradas
└── integration/                            ➕ MigrationV8IT, DependentFlowIT
                                            ✏️ SecrecyIT (dependentes), OpenApiContractIT (001 + 002 + 003 + 004)

frontend/src/
├── api/groups.ts                           ✏️ Participant, GroupDetail (myDependents, bloqueios)
├── api/dependents.ts                       ➕ adicionar, renomear, remover, resultados, lista, saída
├── lib/schemas.ts                          ✏️ zod do nome do dependente
├── components/group/
│   ├── DependentsCard.tsx                  ➕ "Meus dependentes"
│   ├── DependentForm.tsx                   ➕ nome (2–80)
│   ├── DependentSecretFriendCard.tsx       ➕ "Amigo secreto do Lucas"
│   ├── ParticipantList.tsx                 ✏️ "(dependente de Ana)"; aviso ao remover responsável
│   ├── WishlistEditor.tsx                  ✏️ dono como parâmetro
│   ├── ConfirmationCard.tsx                ✏️ "Confirmar participação (você e Lucas)"
│   └── ExitRequest*.tsx                    ✏️ nome do dependente e do responsável
└── pages/GroupPage.tsx                     ✏️ cartões novos
```

**Structure Decision**: mesma estrutura das features anteriores (monorepo `backend/` +
`frontend/`, backend por feature com controller → service → repository). Os dependentes ganham um
pacote próprio (`dependent`) para as regras de cadastro; o que é do sorteio fica em `draw` (único
que resolve pares), o que é da lista em `wishlist` e o que é de saída em `exitrequest` —
cada um recebe o dependente já autorizado pelo `DependentLookup`. Os nomes exatos dos componentes
do frontend serão confirmados contra o código em `/speckit-tasks`.

## Riscos e mitigação

| Risco | Mitigação |
|-------|-----------|
| Alguém além do responsável obter o resultado do dependente | Rota sem id, derivada de `guardian_id = sessão` (R38); nada no `GroupDetail` nem em SSE; `SecrecyIT` com organizador, outro responsável e quem tirou o dependente (SC-002) |
| Dependente conseguir autenticar | `CHECK` no banco (sem e-mail nem senha); login só por e-mail; teste de login (R42) |
| Sorteio deixar de ser uniforme ao incluir as restrições | Só rejeição de permutações inteiras, sem correções locais; teste estatístico (R35, R42) |
| Sorteio impossível travar ou demorar | Viabilidade por emparelhamento antes de sortear; motivo `no-valid-draw` na tela; limite de 200.000 tentativas com probabilidade de esgotar desprezível |
| Dependentes órfãos (sem responsável ou sem participação) | FK composta com `CASCADE`; serviço apaga o `app_user`; consultas do quickstart §3 |
| Confirmação do dependente dessincronizada | Espelho gravado na mesma transação e no mesmo `GroupLock` de toda mudança do responsável; testes de confirmar, desconfirmar, reset e organizador (R36) |
| Mudança de `exit_request` quebrar os avisos da 002 | `requested_by` preenchido com `user_id` na migração; testes da 002 continuam verdes; `MigrationV8IT` com pedidos antigos |
| Telas ou consultas tratarem dependente como conta | Nenhuma rota lista `app_user`; `GroupSummary` só de quem tem sessão; revisão das consultas por `kind` nas tarefas |

## Complexity Tracking

Não se aplica: o Constitution Check não teve violações.
