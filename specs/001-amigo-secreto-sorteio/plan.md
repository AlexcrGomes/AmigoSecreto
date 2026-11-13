# Implementation Plan: Amigo Secreto — Grupos e Sorteio Sigiloso

**Branch**: `001-amigo-secreto-sorteio` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-amigo-secreto-sorteio/spec.md`

## Summary

Sistema web mobile-first de amigo secreto. Usuários criam conta, criam grupos com código único
de 8 caracteres e reúnem participantes pelo código. O organizador dispara um sorteio **único,
atômico e no servidor**, que gera um *derangement* uniformemente aleatório por
embaralhamento + rejeição com `SecureRandom` (refaz sempre que alguém tira a si mesmo). Todos os
participantes são avisados ao mesmo tempo por SSE, e cada um revela **apenas o próprio** amigo
secreto. Os pares ficam cifrados em repouso (AES-256-GCM).

Abordagem técnica: monólito Spring Boot 4.1 (Java 25) com sessão em cookie HttpOnly
(Spring Session JDBC) + PostgreSQL 18 com Flyway; SPA React 19 + Vite 8 + Tailwind 4 servida
por nginx, que faz proxy de `/api` (mesma origem). Tudo sobe com Docker Compose; o CI no GitHub
Actions roda testes, cobertura ≥ 80%, CodeQL, Trivy e Gitleaks.
Decisões detalhadas em [research.md](./research.md).

## Technical Context

**Language/Version**: Java 25 LTS (backend); TypeScript 6.0 (frontend; TS 7 incompatível com
typescript-eslint — research R1)

**Primary Dependencies**: Spring Boot 4.1.2 (Web MVC, Security 7, Data JPA, Validation,
Actuator), Spring Session JDBC, Flyway, Bucket4j + Caffeine, springdoc-openapi 3.2 (só no perfil
`dev`); React 19.3, Vite 8.3, Tailwind CSS 4.3, React Router 8.5, TanStack Query 5, react-hook-form
7 + zod 4

**Storage**: PostgreSQL 18.7 — tabelas `app_user`, `secret_group`, `group_member`,
`draw_assignment` (receiver cifrado) e as do Spring Session ([data-model.md](./data-model.md))

**Testing**: JUnit 5, Mockito, AssertJ, spring-security-test, Testcontainers 2.0.5 (PostgreSQL
real), JaCoCo 0.8.16; Vitest 5.1, React Testing Library 16.3, MSW, `@vitest/coverage-v8`

**Target Platform**: containers Linux (Docker Compose); navegadores móveis modernos (Chrome
Android, Safari iOS) e desktop; desenvolvimento local com Docker no WSL

**Project Type**: aplicação web (backend REST + SPA)

**Performance Goals**: sorteio de 100 participantes concluído em < 1 s; telas respondendo em
≤ 2 s com 100 usuários simultâneos (SC-008); aviso do sorteio em ≤ 5 s (SC-004)

**Constraints**: sigilo absoluto dos pares (Constituição I); funcionamento a partir de 360px;
somente Docker e Git na máquina; nenhum segredo versionado; imagens não-root com versões fixadas

**Scale/Scope**: grupos de 3 a 100 participantes; 1 instância do backend; 6 telas; 13 rotas REST
+ 1 fluxo SSE ([contracts/](./contracts/))

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Princípio | Como o plano cumpre | Pré-pesquisa | Pós-design |
|---|-----------|---------------------|:---:|:---:|
| I | Sigilo (não negociável) | Só a rota `my-secret-friend` devolve um par e usa sempre o usuário da sessão; não há `findAll` nem endpoint de "todos os pares"; respostas de `draw` e SSE sem pares; `404` para não-membros; receiver cifrado com AES-256-GCM (R9); teste de integração de sigilo | ✅ | ✅ |
| II | Sorteio justo e simultâneo (não negociável) | Embaralhamento + rejeição com `SecureRandom` (R6); transação + `SELECT … FOR UPDATE`, estado `OPEN→DRAWN` sem volta (R7); mínimo de 3; evento após o commit (R8) | ✅ | ✅ |
| III | Segurança por padrão | Spring Security com sessão em cookie HttpOnly/Secure/SameSite + CSRF (R2); BCrypt-12 (R3); rate limit em login, cadastro, código e sorteio (R4); código com ~39,6 bits (R5); Bean Validation; JPA parametrizado; segredos via `.env`; CSP e cabeçalhos no nginx (R10) | ✅ | ✅ |
| IV | Testes obrigatórios (não negociável) | Pirâmide de testes em R11; teste primeiro nas regras críticas; propriedade com 10.000 execuções; Testcontainers (sem H2); gate de 80% (JaCoCo e Vitest) | ✅ | ✅ |
| V | Varredura contínua | `security.yml`: CodeQL, Trivy fs/imagem, `npm audit`, Gitleaks; HIGH/CRITICAL bloqueiam; Dependabot (R12) | ✅ | ✅ |
| VI | Mobile-first e moderno | Tailwind 4 com tokens; contrato de telas com alvos de 44px, WCAG AA, estados de carregamento/erro e pt-BR ([ui-routes.md](./contracts/ui-routes.md)) | ✅ | ✅ |
| VII | Containerização | Compose com 3 serviços, multi-stage, não-root, versões fixadas, healthchecks, configuração via env; só o frontend publica porta (R10) | ✅ | ✅ |
| VIII | Simplicidade | Monólito em camadas organizado por feature; uma instância; sem Redis nem broker; dependências novas justificadas (abaixo) | ✅ | ✅ |
| — | Stack obrigatória | Java + Spring Boot + Maven, Spring Security, PostgreSQL + JPA + Flyway, React + Vite + TS, Tailwind, Docker, GitHub Actions | ✅ | ✅ |
| — | `ddl-auto` | `validate` em todos os perfis, exceto testes | ✅ | ✅ |

**Resultado**: nenhum desvio. *Complexity Tracking* não se aplica.

**Dependências além da stack mínima e sua justificativa** (Constituição VIII):

| Dependência | Motivo |
|-------------|--------|
| Spring Session JDBC | sessões sobrevivem a reinícios sem um serviço extra (R2) |
| Bucket4j + Caffeine | rate limiting exigido por FR-004 e FR-013 (R4) |
| springdoc-openapi | documentação OpenAPI exigida pela Constituição VIII; só no perfil `dev` |
| TanStack Query | cache, refetch e polling de contingência do estado do grupo (R8) |
| react-hook-form + zod | formulários acessíveis com a mesma validação do backend |
| MSW | testes de frontend sem backend real |

## Project Structure

### Documentation (this feature)

```text
specs/001-amigo-secreto-sorteio/
├── plan.md              # Este arquivo
├── research.md          # Fase 0: decisões técnicas (R1–R12)
├── data-model.md        # Fase 1: tabelas, estados e invariantes
├── quickstart.md        # Fase 1: como subir, testar e validar
├── contracts/
│   ├── openapi.yaml     # API REST /api/v1
│   ├── sse-events.md    # eventos em tempo real
│   └── ui-routes.md     # telas e comportamento
├── checklists/
│   └── requirements.md  # checklist de qualidade da spec
└── tasks.md             # Fase 2 (/speckit-tasks — ainda não criado)
```

### Source Code (repository root)

```text
compose.yaml                      # db + backend + frontend
.env.example                      # modelo de variáveis (sem segredos reais)
.github/
├── workflows/
│   ├── ci.yml                    # build, testes, cobertura, lint, imagens + job de aprovação manual
│   └── security.yml              # CodeQL, Trivy, npm audit, Gitleaks
├── pull_request_template.md      # checklist dos Princípios I a VIII
└── dependabot.yml
CONTRIBUTING.md                   # fluxo de PR, aprovação manual e proteção da main

backend/
├── Dockerfile                    # multi-stage, não-root
├── pom.xml                       # Spring Boot 4.1.2, Java 25, JaCoCo, Spotless
└── src/
    ├── main/
    │   ├── java/br/com/amigosecreto/
    │   │   ├── AmigoSecretoApplication.java
    │   │   ├── config/           # SecurityConfig, SessionConfig, CryptoConfig, OpenApiConfig
    │   │   ├── common/           # ProblemDetail handler, exceções de domínio, Clock
    │   │   ├── ratelimit/        # RateLimiter (Bucket4j + Caffeine)
    │   │   ├── auth/             # AuthController, AuthService, AppUser, AppUserRepository, dto/
    │   │   ├── group/            # GroupController, GroupService, SecretGroup, GroupMember,
    │   │   │                     #   repositories, GroupCodeGenerator, dto/
    │   │   ├── draw/             # DrawController, DrawService, DerangementGenerator,
    │   │   │                     #   DrawAssignment, SecretFriendService,
    │   │   │                     #   AssignmentCipher (AES-256-GCM com AAD, chamado pelos serviços)
    │   │   └── events/           # GroupEventPublisher, SseEmitterRegistry, GroupEventsController
    │   └── resources/
    │       ├── application.yml   # perfis default/dev/test; tudo via variáveis de ambiente
    │       └── db/migration/     # V1..V4 (data-model.md)
    └── test/java/br/com/amigosecreto/
        ├── auth/ group/ draw/ ratelimit/   # testes unitários (domínio e serviço) por feature
        ├── web/                            # @WebMvcTest: segurança, CSRF, validação, erros
        └── integration/                    # @SpringBootTest + Testcontainers
                                            #   (fluxo completo, concorrência do sorteio, sigilo)

frontend/
├── Dockerfile                    # node build → nginx-unprivileged
├── nginx.conf                    # SPA fallback, proxy /api (SSE sem buffer), cabeçalhos de segurança
├── package.json / package-lock.json
├── vite.config.ts / vitest.config.ts / eslint.config.js / tsconfig.json
├── index.html
└── src/
    ├── main.tsx / App.tsx / index.css   # Tailwind 4 (@import "tailwindcss"; @theme)
    ├── api/                      # client.ts (fetch + CSRF + Problem), auth.ts, groups.ts
    ├── auth/                     # AuthProvider, RequireAuth
    ├── hooks/                    # useGroup, useGroupEvents (SSE + contingência)
    ├── components/
    │   ├── ui/                   # Button, Input, Card, Badge, Modal, Toast, Spinner
    │   └── group/                # ShareCode, ParticipantList, DrawButton, SecretFriendCard
    ├── pages/                    # Login, Register, Home, CreateGroup, JoinGroup, Group
    ├── lib/                      # schemas zod, formatação do código
    └── test/                     # setup do Vitest, handlers MSW
    # testes colocados ao lado do código: *.test.ts(x)
```

**Structure Decision**: aplicação web com `backend/` e `frontend/` no mesmo repositório
(Constituição VIII). O backend é organizado **por feature** (`auth`, `group`, `draw`, `events`),
com camadas controller → service → repository dentro de cada pacote. As regras de domínio
críticas (`DerangementGenerator`, `GroupCodeGenerator`, `AssignmentCipher`) são classes puras e
testáveis sem Spring. O frontend é uma SPA servida por nginx, que também faz o proxy da API, o
que deixa tudo numa única origem.

## Riscos e mitigação

| Risco | Mitigação |
|-------|-----------|
| Vazamento de par por erro de autorização | rota única, sem parâmetro de usuário; teste de integração de sigilo com N usuários; aprovação manual obrigatória de todo PR, só depois do CI e das varreduras verdes, com a checklist dos princípios (Constituição, gate 7) |
| Perda de `DRAW_ENCRYPTION_KEY` torna os pares ilegíveis | documentar backup da chave junto ao backup do banco; o app falha ao iniciar sem a chave |
| Docker no WSL e caminhos do Windows | comandos do quickstart executados no WSL (`/mnt/c/...`); `.gitattributes` com LF |
| SSE cortado por proxy | `proxy_buffering off`, heartbeat de 25 s, reconexão + polling de contingência de 10 s |
| Versões novas com incompatibilidades (Boot 4, Vite 8, TS 6) | versões fixadas; Dependabot com CI completo em cada atualização |

## Complexity Tracking

Não se aplica: o Constitution Check não teve violações.
