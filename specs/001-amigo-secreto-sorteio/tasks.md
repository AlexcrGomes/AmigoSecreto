---

description: "Lista de tarefas da feature 001 — Amigo Secreto (grupos e sorteio sigiloso)"
---

# Tasks: Amigo Secreto — Grupos e Sorteio Sigiloso

**Input**: Design documents from `/specs/001-amigo-secreto-sorteio/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **OBRIGATÓRIOS.** O usuário pediu testes unitários, e a Constituição IV os torna
não negociáveis. Em cada história, as tarefas da seção *Tests* vêm **antes** da implementação e
MUST falhar antes do código existir (Red → Green → Refactor). Backend: JUnit 5 + Mockito +
AssertJ; integração com Testcontainers (PostgreSQL 18.7, nunca H2). Frontend: Vitest + React
Testing Library + MSW. Gate de cobertura: 80% de linhas.

**Organization**: tarefas agrupadas por história de usuário (US1–US4 da spec).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: história de usuário da tarefa (US1, US2, US3, US4)

## Path Conventions

- Backend: `backend/src/main/java/br/com/amigosecreto/…` (abreviado como `…/` nas tarefas) e
  `backend/src/test/java/br/com/amigosecreto/…` (abreviado como `test/…/`)
- Migrações: `backend/src/main/resources/db/migration/`
- Frontend: `frontend/src/…`; testes colocados ao lado do código (`*.test.ts(x)`)
- Docker roda **no WSL**: todo comando de build/teste segue o [quickstart.md](./quickstart.md)

## Regras transversais (valem para TODAS as tarefas)

- **Sigilo (Constituição I)**: nenhum DTO, log, mensagem de erro ou evento SSE pode conter um
  par (quem tirou quem). Só `GET /api/v1/groups/{groupId}/my-secret-friend` devolve um amigo
  secreto, e sempre para o usuário da sessão.
- Mensagens ao usuário em **pt-BR**; erros da API em `application/problem+json` (RFC 9457).
- Nada de segredos no código: tudo via variáveis de ambiente (`.env`, que não é versionado).
- Versões fixadas conforme [research.md R1](./research.md); nada de `latest`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: esqueleto dos projetos, build em container, Docker Compose e CI.

- [X] T001 Criar a estrutura de pastas `backend/`, `frontend/`, `.github/workflows/` e o arquivo `.env.example` na raiz com `POSTGRES_DB=amigosecreto`, `POSTGRES_USER=amigo`, `POSTGRES_PASSWORD=` (vazio), `DRAW_ENCRYPTION_KEY=` (vazio, com comentário "openssl rand -base64 32"), `APP_PORT=8080`, `COOKIE_SECURE=false`
- [X] T002 Criar `backend/pom.xml`: parent `spring-boot-starter-parent` **4.1.2**, `java.version` **25**, groupId `br.com.amigosecreto`. Usar os starters modulares do Boot 4, conferidos no BOM 4.1.2: Web MVC, Security, Data JPA, Validation, Actuator, Flyway (+ `flyway-database-postgresql`), Spring Session JDBC, driver `postgresql`. Extras: `com.bucket4j:bucket4j_jdk17-core:8.22.0`, `caffeine` (versão do BOM), `springdoc-openapi-starter-webmvc-ui:3.2.0`. Testes: starter de teste, `spring-security-test`, `org.testcontainers:testcontainers-postgresql:2.0.5`, `testcontainers-junit-jupiter`. Plugins: `jacoco-maven-plugin:0.8.16` com regra `check` na fase `verify` (LINE COVEREDRATIO ≥ **0.80**, excluindo `AmigoSecretoApplication` e `config/**`) e `spotless-maven-plugin:3.11.0` com palantir-java-format (`check` na fase `verify`)
- [X] T003 [P] Criar `…/AmigoSecretoApplication.java` (com `@EnableScheduling`) e `backend/src/main/resources/application.yml` com perfis `default`, `dev` e `test`: datasource via `DB_URL`/`POSTGRES_USER`/`POSTGRES_PASSWORD`; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`; `spring.threads.virtual.enabled=true`; `server.servlet.session.timeout=8h`; cookie de sessão `http-only: true`, `secure: ${COOKIE_SECURE:true}`, `same-site: lax`; `spring.session.jdbc.initialize-schema=never`; `jdbc.time_zone=UTC`; Actuator expondo só `health`; springdoc desligado fora do perfil `dev`; propriedade `app.draw.encryption-key: ${DRAW_ENCRYPTION_KEY}`; propriedades `app.rate-limit.<política>.capacity` e `app.rate-limit.<política>.window` para `login-failure`, `register`, `join-invalid-code` e `draw`, com os valores da [research R4](./research.md) como padrão; no perfil `test`, só `app.rate-limit.register.capacity: 10000` (os testes de integração criam mais de 100 usuários)
- [X] T004 [P] Criar `frontend/package.json` com versões exatas: react/react-dom 19.3.x, vite 8.3.x, `@vitejs/plugin-react` 6.3.x, tailwindcss + `@tailwindcss/vite` 4.3.x, typescript **6.0.3**, react-router 8.5.x, `@tanstack/react-query` 5.107.x, react-hook-form 7.92.x, zod 4.6.x, vitest 5.1.x, `@vitest/coverage-v8`, `@testing-library/react` 16.3.x, `@testing-library/user-event`, `@testing-library/jest-dom`, jsdom, msw, eslint 10.x, typescript-eslint 8.72.x, `eslint-plugin-react-hooks`, prettier. Scripts: `dev`, `build` (`tsc -b && vite build`), `lint`, `format` (`prettier --write .`), `format:check` (`prettier --check .`), `test`, `test:coverage`. Gerar o `package-lock.json` com `npm install` num container `node:24.22.3-alpine3.24`
- [X] T005 [P] Criar `frontend/tsconfig.json` (`strict: true`, `noUncheckedIndexedAccess: true`) e `frontend/vite.config.ts` com os plugins react e tailwind, e proxy de dev `/api` → `http://localhost:8081`
- [X] T006 [P] Criar `frontend/eslint.config.js` (flat config: typescript-eslint strict + react-hooks) e `frontend/.prettierrc`
- [X] T007 [P] Criar `frontend/vitest.config.ts` (ambiente jsdom, `setupFiles: src/test/setup.ts`, cobertura v8 com `thresholds.lines: 80` excluindo `main.tsx` e `src/test/**`), `frontend/src/test/setup.ts` (jest-dom e ciclo de vida do MSW) e `frontend/src/test/server.ts` (servidor MSW com handlers vazios)
- [X] T008 [P] Criar `frontend/index.html` (`lang="pt-BR"`, `<meta name="viewport" content="width=device-width, initial-scale=1">`, título "Amigo Secreto") e `frontend/src/index.css` com `@import "tailwindcss";` e tokens `@theme`: cor primária, superfícies, texto, perigo, sucesso, raios e sombras, com variantes escuras via `prefers-color-scheme`
- [X] T009 [P] Criar `backend/Dockerfile` multi-stage: build em `maven:3.10.0-eclipse-temurin-25` (`mvn -B -DskipTests package`, aproveitando o cache de dependências); runtime em `eclipse-temurin:25-jre-alpine-3.24`, usuário não-root (`appuser`, UID 10001), `EXPOSE 8080`, `HEALTHCHECK` em `/actuator/health`, `ENTRYPOINT java -XX:MaxRAMPercentage=75 -jar app.jar`. Criar também `backend/.dockerignore` (`target/`)
- [X] T010 [P] Criar `frontend/Dockerfile` multi-stage: build em `node:24.22.3-alpine3.24` (`npm ci && npm run build`); runtime em `nginxinc/nginx-unprivileged` stable-alpine3.24 com versão exata fixada. Criar `frontend/nginx.conf`: `listen 8080`; SPA com `try_files $uri /index.html`; `location /api/` com proxy para `http://backend:8080` e `proxy_set_header X-Forwarded-For $remote_addr` (sobrescreve, não acrescenta, para o cliente não forjar o IP usado no rate limit); `location ~ ^/api/v1/groups/[^/]+/events$` com `proxy_buffering off`, `proxy_cache off`, `proxy_read_timeout 1h`, `proxy_http_version 1.1` e `Connection ""`; cabeçalhos `Content-Security-Policy "default-src 'self'; connect-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"`, `X-Content-Type-Options nosniff`, `Referrer-Policy no-referrer`, `Permissions-Policy "camera=(), microphone=(), geolocation=()"`; cache longo para `/assets/`. Criar também `frontend/.dockerignore` (`node_modules/`, `dist/`, `coverage/`)
- [X] T011 Criar `compose.yaml` com os serviços `db` (build de `db/Dockerfile`: `postgres:18.7-alpine3.24` com `apk upgrade` e o `gosu` trocado pelo `su-exec` do Alpine, para zerar os HIGH/CRITICAL do Trivy; volume `pgdata`, healthcheck `pg_isready`, **sem** `ports`), `backend` (build `./backend`, env `DB_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DRAW_ENCRYPTION_KEY`, `COOKIE_SECURE`, `depends_on: db: condition: service_healthy`, **sem** `ports`) e `frontend` (build `./frontend`, `ports: "${APP_PORT:-8080}:8080"`, `depends_on: backend: condition: service_healthy`), todos com `restart: unless-stopped` e `read_only`/`tmpfs` onde couber (depende de T009 e T010)
- [X] T012 [P] Criar `.github/workflows/ci.yml` (PR e push na `main`): job `backend` (Temurin 25 com cache do Maven, `mvn -B verify` — o Testcontainers usa o Docker do runner), job `frontend` (Node 24 com cache, `npm ci`, `npm run lint`, `npm run format:check`, `npm run test:coverage`, `npm run build`), job `docker` (`docker compose build`) e job `aprovacao` (só em `pull_request`; `needs: [backend, frontend, docker]`; `environment: aprovacao-manual`; apenas registra a aprovação, sem segredos nem checkout), com `permissions: contents: read` e actions fixadas por SHA
- [X] T013 [P] Criar `.github/workflows/security.yml` (PR, push na `main` e `schedule` semanal): CodeQL (`java-kotlin` com build manual via `mvn -B -DskipTests package`, e `javascript-typescript`); Trivy `fs` (`scanners: vuln,secret`, `severity: CRITICAL,HIGH`, `exit-code: 1`, `ignore-unfixed: true`); `npm audit --audit-level=high` em `frontend/`; build das 3 imagens + Trivy `image` com a mesma severidade; Gitleaks com `fetch-depth: 0`. Actions fixadas por SHA; `permissions` mínimas (`security-events: write` só no CodeQL)
- [X] T014 [P] Criar `.github/dependabot.yml` com ecossistemas `maven` (`/backend`), `npm` (`/frontend`), `docker` (`/backend`, `/frontend`) e `github-actions` (`/`), todos semanais
- [X] T015 [P] Criar `.github/pull_request_template.md` (resumo, como testar e a checklist dos Princípios I a VIII da constituição, com destaque para o sigilo dos pares) e `CONTRIBUTING.md` na raiz: fluxo branch → PR → checks (CI e segurança) → aprovação manual no environment `aprovacao-manual` (aba Actions → Review deployments → Approve) → merge; todo novo commit exige nova aprovação; passo a passo para criar o ruleset da `main` e o environment (Settings → Rules e Settings → Environments); aviso de que environment com revisor obrigatório em repositório privado exige GitHub Pro/Team e de que, sem isso, a aprovação manual é o merge feito pelo mantenedor após a checklist

**Checkpoint**: `docker compose build` funciona no WSL e os workflows são válidos (ainda sem código de negócio).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: segurança, erros, usuário, rate limit, base de testes e base do frontend — tudo de que todas as histórias dependem.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase terminar.

### Tests (escrever primeiro)

- [X] T016 [P] Teste `test/…/common/GlobalExceptionHandlerTest.java`: cada exceção de domínio gera um ProblemDetail com o `status` correto, `type` no formato `/problems/<slug>` e `detail` em pt-BR; erros de Bean Validation geram `400` com `errors[{field,message}]`; exceção inesperada gera `500` com `detail` genérico, sem stack trace nem mensagem interna
- [X] T017 [P] Teste `test/…/ratelimit/RateLimiterTest.java`: para cada política de [research R4](./research.md) (`LOGIN_FAILURE` 5/15 min, `REGISTER` 10/h, `JOIN_INVALID_CODE` 10/10 min, `DRAW` 5/min), com a configuração real (valores de R4 fixados no próprio teste, não os do perfil `test`), a tentativa N+1 é negada e o `retryAfterSeconds` é maior que 0; chaves diferentes são independentes; `reset(chave)` libera de novo
- [X] T018 [P] Teste `test/…/web/SecurityConfigTest.java` (`@WebMvcTest` + spring-security-test): rota protegida sem sessão → `401` em `application/problem+json`, sem redirecionamento; `POST` sem `X-XSRF-TOKEN` → `403`; `GET /api/v1/auth/csrf`, `POST /api/v1/auth/register`, `POST /api/v1/auth/login` e `GET /actuator/health` são públicos; a resposta tem `X-Content-Type-Options: nosniff`
- [X] T019 [P] Teste `frontend/src/api/client.test.ts`: envia `credentials: 'same-origin'`; em POST/DELETE envia o cabeçalho `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`; resposta `application/problem+json` vira `ApiError` com `status`, `type`, `detail` e `errors`; `401` dispara o evento `auth:unauthorized`; `429` expõe `retryAfter`
- [X] T020 [P] Testes `frontend/src/components/ui/Button.test.tsx`, `Input.test.tsx` e `Modal.test.tsx`: o Button em `loading` fica desabilitado e mostra o spinner com `aria-busy`; o Input associa `label` e mensagem de erro via `aria-describedby` e marca `aria-invalid`; o Modal usa `role="dialog"`, prende o foco, fecha com Esc e devolve o foco ao gatilho

### Implementation

- [X] T021 Criar a migração `V1__create_users.sql`: tabela `app_user` com `id uuid PRIMARY KEY`, `name varchar(80) NOT NULL`, `email varchar(254) NOT NULL`, `password_hash varchar(100) NOT NULL`, `created_at timestamptz NOT NULL`, constraint `uq_app_user_email UNIQUE (email)`
- [X] T022 Criar a migração `V2__create_spring_session.sql` com o script oficial do Spring Session JDBC para PostgreSQL (`spring_session` e `spring_session_attributes`, com índices). As migrações seguem a ordem de criação (V3 na US2 e V4 na US3), porque o Flyway rejeita versões menores que a última aplicada
- [X] T023 [P] Criar a entidade `…/auth/AppUser.java` (campos de T021; `email` sempre minúsculo e sem espaços nas pontas; `toString` sem `passwordHash`) e `…/auth/AppUserRepository.java` (`findByEmail`, `existsByEmail`)
- [X] T024 [P] Criar `…/common/` com as exceções de domínio (`NotFoundException`, `ForbiddenException`, `ConflictException` com `type`, `UnprocessableException`, `RateLimitExceededException` com `retryAfterSeconds`) e `GlobalExceptionHandler.java` (`@RestControllerAdvice` gerando ProblemDetail com `Retry-After` no `429`), fazendo T016 passar
- [X] T025 [P] Criar `…/common/TimeConfig.java` com um bean `Clock` UTC, usado por todas as datas (`created_at`, `joined_at`, `drawn_at`)
- [X] T026 [P] Criar `…/ratelimit/RateLimitPolicy.java` (enum com as políticas de T017), `…/ratelimit/RateLimitProperties.java` (`@ConfigurationProperties("app.rate-limit")` com `capacity` e `window` por política, validados como positivos) e `…/ratelimit/RateLimiter.java` (Bucket4j + cache Caffeine que expira o bucket após a janela; métodos `consume(policy, key)`, que lança `RateLimitExceededException`, e `reset(policy, key)`), fazendo T017 passar
- [X] T027 Criar `…/config/SecurityConfig.java`: `SecurityFilterChain` stateful com CSRF no padrão SPA (cookie `XSRF-TOKEN` + cabeçalho `X-XSRF-TOKEN`); rotas públicas de T018; o resto autenticado; `AuthenticationEntryPoint` e `AccessDeniedHandler` respondendo ProblemDetail em JSON; `formLogin`, `httpBasic` e `logout` padrão desligados; `sessionFixation().changeSessionId()`; cabeçalhos padrão + `frame-options deny`; CORS desligado (mesma origem); bean `PasswordEncoder` = `DelegatingPasswordEncoder` com padrão `bcrypt` e **custo 12**; bean `SecurityContextRepository` = `HttpSessionSecurityContextRepository`. Fazer T018 passar (depende de T024)
- [X] T028 Criar `…/auth/CurrentUser.java`: helper que obtém o `UUID` e o nome do usuário autenticado a partir do `SecurityContext` (principal = record `AuthenticatedUser(UUID id, String name, String email)`). **Nunca** ler o ID do usuário do corpo nem da URL
- [X] T029 [P] Criar a base de testes de integração `test/…/integration/AbstractIntegrationTest.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, `PostgreSQLContainer("postgres:18.7-alpine3.24")` com `@ServiceConnection`, chave `app.draw.encryption-key` de teste) e `test/…/integration/ApiClient.java`, um helper HTTP que mantém o cookie de sessão e o XSRF de cada usuário simulado (`register`, `login`, `get`, `post`, `delete`)
- [X] T030 [P] Criar `frontend/src/api/client.ts` (`apiFetch` com JSON, CSRF, `ApiError` e evento `auth:unauthorized`) e `frontend/src/api/problem.ts` (tipos do Problem do contrato), fazendo T019 passar
- [X] T031 [P] Criar os componentes `frontend/src/components/ui/` `Button.tsx`, `Input.tsx`, `Card.tsx`, `Badge.tsx`, `Modal.tsx`, `Spinner.tsx` e `Toast.tsx` (provider + hook `useToast`), com Tailwind e alvos de toque ≥ 44×44 px (`min-h-11 min-w-11`), foco visível e contraste AA, fazendo T020 passar
- [X] T032 Criar `frontend/src/main.tsx` e `frontend/src/App.tsx`: `QueryClientProvider` (retry desligado para 4xx), `ToastProvider`, `BrowserRouter` com rotas vazias das 6 telas de [ui-routes.md](./contracts/ui-routes.md) e layout base mobile-first (`max-w-md mx-auto px-4`, sem rolagem horizontal em 360px). Chamar `GET /api/v1/auth/csrf` ao iniciar (depende de T030 e T031)

**Checkpoint**: `mvn -B verify` e `npm run test:coverage` passam; a base de segurança responde 401/403 corretamente.

---

## Phase 3: User Story 1 — Criar conta e acessar o sistema (Priority: P1) 🎯 MVP

**Goal**: cadastro, login, logout e telas protegidas.

**Independent Test**: criar a conta, sair, entrar de novo e ver a tela inicial; abrir uma tela interna sem login leva a `/entrar` (quickstart, passos 1 a 4).

### Tests for User Story 1 ⚠️ (escrever primeiro e ver falhar)

- [X] T033 [P] [US1] Teste `test/…/auth/AuthServiceTest.java` (Mockito): o cadastro normaliza o e-mail (minúsculo + `trim`) e o nome (`trim`); e-mail duplicado → `ConflictException` (`/problems/email-already-used`); a senha é gravada com o `PasswordEncoder`; senha com mais de **72 bytes** em UTF-8 é rejeitada; login com e-mail inexistente e login com senha errada lançam **a mesma** exceção, com a mesma mensagem; com e-mail inexistente, ainda assim chama `passwordEncoder.matches` contra um hash fictício (evita enumeração por tempo); cada falha consome `LOGIN_FAILURE` com a chave `email|ip`; o sucesso chama `reset`
- [X] T034 [P] [US1] Teste `test/…/web/AuthControllerWebTest.java` (`@WebMvcTest`): `POST /api/v1/auth/register` inválido → `400` com `errors` (nome "2–80 caracteres", e-mail "formato válido, ≤ 254", senha "8–64 caracteres, ≤ 72 bytes"); `409` em e-mail duplicado; `201` com corpo `User` sem `password`/`passwordHash`; `POST /login` errado → `401` com `detail` "E-mail ou senha inválidos."; `429` com `Retry-After`; `POST /logout` → `204`; `GET /me` sem sessão → `401`
- [X] T035 [P] [US1] Teste de integração `test/…/integration/AuthFlowIT.java`: register → cookie `SESSION` HttpOnly → `GET /me` 200 → logout → `GET /me` 401; o login emite um ID de sessão **diferente** do anterior (fixation); a 6ª senha errada seguida → `429` (o perfil `test` relaxa só `REGISTER`; o limite real de cadastro é coberto por T017); a sessão fica gravada na tabela `spring_session`
- [X] T036 [P] [US1] Testes `frontend/src/pages/LoginPage.test.tsx` e `RegisterPage.test.tsx` (MSW): validações zod em pt-BR (mensagens de [data-model.md](./data-model.md)); o sucesso navega para `voltar` ou `/`; `401` mostra a mensagem genérica; `429` mostra "Muitas tentativas. Tente novamente em alguns minutos."; `409` no cadastro mostra "Este e-mail já está cadastrado."; **não existe** link "esqueci minha senha" (FR-006); o botão mostra loading durante o envio
- [X] T037 [P] [US1] Teste `frontend/src/auth/RequireAuth.test.tsx`: sem usuário, redireciona para `/entrar?voltar=<rota>`; com usuário, renderiza os filhos; o evento `auth:unauthorized` limpa o usuário e redireciona; usuário logado em `/entrar` é levado para `/`

### Implementation for User Story 1

- [X] T038 [P] [US1] Criar `…/auth/dto/RegisterRequest.java` (`name` `@NotBlank @Size(min=2,max=80)` após trim; `email` `@NotBlank @Email @Size(max=254)`; `password` `@Size(min=8,max=64)` + `@MaxUtf8Bytes(72)`), `LoginRequest.java` e `UserResponse.java` (`id`, `name`, `email`), mais o validador `…/common/validation/MaxUtf8Bytes.java` com seu `MaxUtf8BytesValidator.java`
- [X] T039 [US1] Implementar `…/auth/AuthService.java` (`register` e `authenticate`) conforme T033, usando `RateLimiter` (`REGISTER` por IP; `LOGIN_FAILURE` por `email|ip`), `PasswordEncoder` e `Clock`. Fazer T033 passar (depende de T023, T026, T038)
- [X] T040 [US1] Implementar `…/auth/AuthController.java`: `GET /api/v1/auth/csrf` (204, força a geração do token), `POST /register` (201, já autentica), `POST /login` (200), `POST /logout` (204, invalida a sessão e apaga o cookie), `GET /me`. Autenticar com `request.changeSessionId()` + `SecurityContextRepository.saveContext`, com principal `AuthenticatedUser`. O IP do cliente vem de `request.getRemoteAddr()`, com o nginx configurado para repassar `X-Forwarded-For` e o Spring `server.forward-headers-strategy=native`. Fazer T034 e T035 passar (depende de T027, T028, T039)
- [X] T041 [P] [US1] Criar `frontend/src/lib/schemas.ts` (zod `registerSchema` e `loginSchema` com as mesmas regras e mensagens de [data-model.md](./data-model.md)) e `frontend/src/api/auth.ts` (`getMe`, `register`, `login`, `logout`)
- [X] T042 [US1] Criar `frontend/src/auth/AuthProvider.tsx` (query `me` + mutações de login, cadastro e logout; escuta `auth:unauthorized`) e `frontend/src/auth/RequireAuth.tsx`, fazendo T037 passar (depende de T041)
- [X] T043 [US1] Criar `frontend/src/pages/LoginPage.tsx` e `frontend/src/pages/RegisterPage.tsx` (react-hook-form + zod, campos com `autoComplete` adequado, `inputMode="email"`, link entre as telas, visual de cartão centralizado no celular), fazendo T036 passar (depende de T042)
- [X] T044 [US1] Criar `frontend/src/components/AppHeader.tsx` (nome do app, nome do usuário e botão "Sair") e `frontend/src/pages/HomePage.tsx` com estado vazio provisório ("Você ainda não participa de nenhum grupo"); ligar as rotas `/entrar`, `/cadastro` e `/` (protegida) em `App.tsx`

**Checkpoint**: US1 funciona sozinha (quickstart, passos 1 a 4). Cobertura do backend e do frontend ≥ 80%.

---

## Phase 4: User Story 2 — Criar grupo e reunir participantes pelo código (Priority: P1)

**Goal**: criar grupo com código único, entrar pelo código, listar grupos e ver participantes ao vivo.

**Independent Test**: A cria um grupo; B e C entram com o código (B em minúsculas); os três veem a mesma lista, atualizada sem recarregar (quickstart, passos 5 a 10).

### Tests for User Story 2 ⚠️

- [X] T045 [P] [US2] Teste `test/…/group/GroupCodeGeneratorTest.java`: gera 8 caracteres apenas do alfabeto `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (nunca `0 O 1 I L`); `format("K7PQM2XA")` = `"K7PQ-M2XA"`; `normalize(" k7pq-m2xa ")` = `"K7PQM2XA"`; `normalize` de entrada com tamanho ou caracteres inválidos → `Optional.empty()`; em 10.000 códigos gerados, todos os 31 símbolos aparecem (sanidade de aleatoriedade); usa o `SecureRandom` injetado
- [X] T046 [P] [US2] Teste `test/…/group/GroupServiceTest.java` (Mockito): `create` grava o grupo `OPEN` e insere o criador em `group_member`; colisão de código gera outro, até 5 tentativas; `join` com código válido insere o membro; `join` de quem já é membro é idempotente (não duplica e responde com o grupo); código inexistente → `NotFoundException` + consumo de `JOIN_INVALID_CODE`; grupo `DRAWN` → `ConflictException(/problems/group-already-drawn)`; grupo com **100** membros → `ConflictException(/problems/group-full)`; `join` usa `findByIdForUpdate`; `getDetail` para não-membro → `NotFoundException`; `canDraw` é verdadeiro só para o organizador, com `OPEN` e ≥ 3 membros; `listMine` traz `participantCount` e `organizer`; `create`/`join` publicam `ParticipantsChangedEvent`
- [X] T047 [P] [US2] Teste `test/…/events/SseEmitterRegistryTest.java`: registra emitters por grupo e usuário; o evento `participants-changed` chega a todos os emitters do grupo com `data` contendo **somente** `groupId`, `status` e `participantCount` (assert de que o JSON não tem nomes nem IDs de usuário); emitter com erro é removido; o heartbeat envia o comentário `ping`
- [X] T048 [P] [US2] Teste `test/…/web/GroupControllerWebTest.java` (`@WebMvcTest`): `POST /api/v1/groups` com nome vazio ou de mais de 60 caracteres → `400`; `201` com `Location` e `GroupDetail` (código formatado `XXXX-XXXX`, `maxParticipants: 100`); `POST /groups/join` → 200/404/409/429 conforme o contrato; `GET /groups/{id}` de não-membro → `404`; `GET /groups/{id}/events` de não-membro → `404`
- [X] T049 [P] [US2] Teste de integração `test/…/integration/GroupFlowIT.java`: A cria, B e C entram (B com o código em minúsculas e com hífen); os três recebem a mesma lista de participantes ordenada por entrada; D (não-membro) recebe `404` em `GET /groups/{id}`; 20 `join` concorrentes num grupo com 95 membros resultam em exatamente 100 membros e nos demais com `409 group-full`; a constraint `uq_secret_group_code` existe
- [X] T050 [P] [US2] Teste `frontend/src/lib/groupCode.test.ts` (formatar/normalizar, igual ao backend) e `frontend/src/pages/JoinGroupPage.test.tsx`: máscara `XXXX-XXXX` com maiúsculas automáticas, `autoCapitalize="characters"`, `autoCorrect="off"`, `spellCheck=false`; o parâmetro `?codigo=` preenche o campo; 404 → "Código inválido."; 409 já sorteado → "O sorteio deste grupo já foi realizado."; 409 cheio → "Este grupo está cheio."; o sucesso navega para `/grupos/:id`
- [X] T051 [P] [US2] Testes `frontend/src/pages/HomePage.test.tsx` (lista os cartões com nome, contagem e selos "Aguardando sorteio"/"Sorteado"/"Organizador"; estado vazio; botões "Criar grupo" e "Entrar com código") e `frontend/src/pages/CreateGroupPage.test.tsx` (nome obrigatório de até 60 caracteres; o sucesso navega para o grupo)
- [X] T052 [P] [US2] Testes `frontend/src/pages/GroupPage.open.test.tsx` (status `OPEN`: código em destaque; "Copiar" usa a clipboard e mostra um toast; "Compartilhar" usa `navigator.share` com o link `/entrar-no-grupo?codigo=XXXX-XXXX` e cai para "Copiar" se não houver suporte; lista de participantes com selo "Organizador" e contador) e `frontend/src/hooks/useGroupEvents.test.ts` (com um `EventSource` falso: `participants-changed` invalida a query do grupo; em erro ativa polling de 10 s; fecha a conexão ao desmontar)

### Implementation for User Story 2

- [X] T053 [US2] Criar a migração `V3__create_groups.sql`: `secret_group` (`id uuid PK`, `name varchar(60) NOT NULL`, `code char(8) NOT NULL`, `organizer_id uuid NOT NULL REFERENCES app_user(id)`, `status varchar(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','DRAWN'))`, `created_at timestamptz NOT NULL`, `drawn_at timestamptz NULL`, `version bigint NOT NULL DEFAULT 0`, `CONSTRAINT uq_secret_group_code UNIQUE (code)`, `CONSTRAINT ck_secret_group_drawn CHECK ((status = 'DRAWN') = (drawn_at IS NOT NULL))`) e `group_member` (`group_id uuid REFERENCES secret_group(id)`, `user_id uuid REFERENCES app_user(id)`, `joined_at timestamptz NOT NULL`, `PRIMARY KEY (group_id, user_id)`), com o índice `ix_group_member_user (user_id)`
- [X] T054 [P] [US2] Criar `…/group/GroupStatus.java` (`OPEN`, `DRAWN`), as entidades `…/group/SecretGroup.java` (`@Version`; método de domínio `markDrawn(Instant)` que só aceita sair de `OPEN`) e `…/group/GroupMember.java` (`@EmbeddedId GroupMemberId(groupId, userId)`), e os repositórios `SecretGroupRepository.java` (`findByCode`, `findByIdForUpdate` com `@Lock(PESSIMISTIC_WRITE)`) e `GroupMemberRepository.java` (`existsById`, `countByIdGroupId`, participantes ordenados por `joined_at` com o nome do usuário, grupos do usuário com contagem)
- [X] T055 [P] [US2] Implementar `…/group/GroupCodeGenerator.java` (alfabeto e regras de [research R5](./research.md), `SecureRandom` injetado), fazendo T045 passar
- [X] T056 [P] [US2] Criar os DTOs `…/group/dto/CreateGroupRequest.java` (`name` `@NotBlank @Size(max=60)` após trim), `JoinGroupRequest.java`, `GroupSummary.java`, `GroupDetail.java` e `Participant.java`, exatamente como os schemas de [openapi.yaml](./contracts/openapi.yaml)
- [X] T057 [US2] Criar `…/events/GroupEvents.java` (records `ParticipantsChangedEvent`, `GroupDrawnEvent` e `MembershipRevokedEvent`, só com IDs, status e contagem), `…/events/SseEmitterRegistry.java` (mapa grupo → emitters por usuário; `@TransactionalEventListener(phase = AFTER_COMMIT)` envia os eventos conforme [sse-events.md](./contracts/sse-events.md); heartbeat `@Scheduled` a cada 25 s; timeout de 1 h) e `…/events/GroupEventsController.java` (`GET /api/v1/groups/{groupId}/events`, checa a participação, `404` para não-membro), fazendo T047 passar
- [X] T058 [US2] Implementar `…/group/GroupService.java` (`create`, `join`, `getDetail`, `listMine`) conforme T046, com o lock da linha do grupo em `join`, limite de 100, `RateLimiter` (`JOIN_INVALID_CODE` por usuário) e publicação de `ParticipantsChangedEvent`. Fazer T046 passar (depende de T054 a T057)
- [X] T059 [US2] Implementar `…/group/GroupController.java` (`GET/POST /api/v1/groups`, `POST /api/v1/groups/join`, `GET /api/v1/groups/{groupId}`), usando sempre `CurrentUser` e nunca um ID de usuário vindo do cliente, fazendo T048 e T049 passar (depende de T058)
- [X] T060 [P] [US2] Criar `frontend/src/lib/groupCode.ts` e `frontend/src/api/groups.ts` (`listGroups`, `createGroup`, `joinGroup`, `getGroup`, com os tipos do contrato), fazendo a parte de `groupCode` de T050 passar
- [X] T061 [P] [US2] Criar os hooks `frontend/src/hooks/useGroup.ts` (query `['group', id]`) e `frontend/src/hooks/useGroupEvents.ts` (`EventSource` em `/api/v1/groups/{id}/events`; invalida a query; polling de contingência de 10 s; ainda sem tratar `group-drawn`, que vem na US3, nem `membership-revoked`, que vem na US4), fazendo a parte de hooks de T052 passar
- [X] T062 [US2] Completar `frontend/src/pages/HomePage.tsx` (cartões dos grupos) e criar `frontend/src/pages/CreateGroupPage.tsx` e `frontend/src/pages/JoinGroupPage.tsx`, fazendo T050 e T051 passar (depende de T060)
- [X] T063 [US2] Criar `frontend/src/components/group/ShareCode.tsx`, `frontend/src/components/group/ParticipantList.tsx` e `frontend/src/pages/GroupPage.tsx` (estado `OPEN`: nome, selo, código, lista ao vivo), e ligar as rotas `/grupos/novo`, `/entrar-no-grupo` e `/grupos/:id`, fazendo T052 passar (depende de T061)

**Checkpoint**: US1 + US2 funcionam (quickstart, passos 5 a 10, sem o sorteio).

---

## Phase 5: User Story 3 — Realizar o sorteio e ver o próprio amigo secreto (Priority: P1)

**Goal**: sorteio único, atômico e sigiloso, com aviso simultâneo e revelação individual.

**Independent Test**: com um grupo de 3 ou mais pessoas, o organizador sorteia; todos veem "Sorteado" em ≤ 5 s; cada um revela um nome diferente do próprio; ninguém obtém o par de outro (quickstart, passos 12 a 16 e "Verificação de sigilo").

**Depende de**: US2 (precisa de grupos e participantes).

### Tests for User Story 3 ⚠️ (regras críticas — teste primeiro, Constituição IV)

- [X] T064 [P] [US3] Teste `test/…/draw/DerangementGeneratorTest.java`: **propriedade** — 10.000 execuções com `n` aleatório entre 3 e 100: o resultado é uma bijeção (todos dão e recebem exatamente uma vez) e não tem ponto fixo (ninguém tira a si mesmo). Com um `Random` controlado cujo primeiro embaralhamento tem ponto fixo, verifica que a tentativa é descartada e refeita. Um `Random` que sempre gera ponto fixo leva a uma exceção após **1.000** tentativas. `n < 3` lança `IllegalArgumentException`. **Uniformidade**: para `n=3` (2 derangements) e `n=4` (9 derangements), em 90.000 execuções cada resultado aparece com frequência dentro de ±5% do esperado
- [X] T065 [P] [US3] Teste `test/…/draw/AssignmentCipherTest.java`: `decrypt(encrypt(x, aad), aad) == x`; dois `encrypt` do mesmo valor geram bytes diferentes (IV aleatório de 12 bytes); AAD diferente, chave errada ou byte adulterado → exceção; chave com tamanho diferente de 32 bytes → falha ao construir; o resultado não contém o UUID em texto nem em bytes
- [X] T066 [P] [US3] Teste `test/…/draw/DrawServiceTest.java` (Mockito): não-membro → `NotFoundException`; membro que não é organizador → `ForbiddenException`; menos de 3 membros → `UnprocessableException(/problems/not-enough-participants)`; grupo `DRAWN` → `ConflictException(/problems/group-already-drawn)`; no sucesso, usa `findByIdForUpdate`, grava **n** pares cifrados com AAD `groupId‖giverId`, marca `DRAWN` com `drawn_at` = `Clock`, publica `GroupDrawnEvent` e devolve `DrawResult` **sem pares**; consome `DRAW` por grupo; se o gerador ou a gravação falhar, a exceção propaga (rollback) e nenhum evento é publicado
- [X] T067 [P] [US3] Teste `test/…/draw/SecretFriendServiceTest.java`: não-membro → `NotFoundException`; grupo `OPEN` → `ConflictException(/problems/group-not-drawn)`; busca **somente** por `(groupId, idDoUsuarioDaSessao)`, decifra com o AAD correto e devolve só o nome
- [X] T068 [P] [US3] Teste `test/…/web/DrawControllerWebTest.java`: `POST /api/v1/groups/{id}/draw` → 200 com exatamente as chaves `groupId`, `status` e `drawnAt`; 403/404/409/422/429 conforme o contrato; `GET /my-secret-friend` → 200 com exatamente a chave `name` e o cabeçalho `Cache-Control: no-store`; a rota não aceita parâmetro de usuário
- [X] T069 [P] [US3] Teste de integração `test/…/integration/DrawConcurrencyIT.java`: grupo com 10 membros; 10 `POST /draw` simultâneos do organizador → exatamente **1** resposta 200 e as demais 409 (ou 429); a tabela `draw_assignment` tem exatamente 10 linhas; depois disso, `join` → `409` (sair e remover após o sorteio são testados na US4, em T086)
- [X] T070 [P] [US3] Teste de integração **de sigilo** `test/…/integration/SecrecyIT.java` (Constituição I): grupo com 5 membros sorteado. (a) Cada usuário obtém em `my-secret-friend` um nome diferente do próprio, e os 5 resultados formam um derangement. (b) Para cada usuário, incluindo o organizador, as respostas de `GET /groups`, `GET /groups/{id}`, `POST /draw` e os eventos SSE recebidos **não contêm** nenhuma chave de par (`receiver`, `secretFriend`, `assignment`, `giver`) nem a relação entre usuários. (c) Não existe rota que receba o ID de outro usuário para o resultado (`GET /groups/{id}/my-secret-friend?userId=…` ignora o parâmetro). (d) Um `SELECT` cru em `draw_assignment.receiver_enc` não contém nenhum UUID de usuário, nem em texto nem em bytes. (e) Com `OutputCaptureExtension`, os logs da execução inteira não contêm os IDs de quem foi tirado
- [X] T071 [P] [US3] Testes `frontend/src/components/group/DrawButton.test.tsx` (organizador com menos de 3 participantes: desabilitado com a dica "São necessários pelo menos 3 participantes"; o modal de confirmação avisa que ninguém mais poderá entrar ou sair; confirmar chama `POST /draw` com loading; 409/422/429 mostram o `detail`; não-organizador não vê o botão) e `frontend/src/components/group/SecretFriendCard.test.tsx` (nome oculto `••••••` por padrão; "Revelar" busca e mostra o nome; "Ocultar" esconde de novo; nada é escrito em `localStorage`/`sessionStorage`; a query usa `gcTime: 0`)
- [X] T072 [P] [US3] Teste `frontend/src/pages/GroupPage.drawn.test.tsx`: no status `DRAWN`, mostra o selo "Sorteado" com a data e o `SecretFriendCard`, e **não** mostra entrar, sair, remover nem sortear; a lista mostra só nomes; ao receber `group-drawn` estando em `OPEN`, a tela passa para `DRAWN` e mostra o aviso "O sorteio foi realizado! Toque em Revelar para ver quem você tirou."

### Implementation for User Story 3

- [X] T073 [US3] Criar a migração `V4__create_draw_assignment.sql`: `draw_assignment` (`group_id uuid NOT NULL`, `giver_id uuid NOT NULL`, `receiver_enc bytea NOT NULL`, `created_at timestamptz NOT NULL`, `PRIMARY KEY (group_id, giver_id)`, `FOREIGN KEY (group_id, giver_id) REFERENCES group_member(group_id, user_id)`). Sem coluna de receiver em claro e sem índice sobre `receiver_enc`
- [X] T074 [P] [US3] Implementar `…/draw/DerangementGenerator.java` (classe pura: `Collections.shuffle(copia, random)` + rejeição se houver ponto fixo; máximo de **1.000** tentativas; valida a bijeção antes de devolver `Map<UUID, UUID>`), fazendo T064 passar
- [X] T075 [P] [US3] Implementar `…/draw/AssignmentCipher.java` (`AES/GCM/NoPadding`, chave de 256 bits, IV de 12 bytes do `SecureRandom`, tag de 128 bits, saída `IV‖ciphertext‖tag`, AAD `groupId‖giverId`) e `…/config/CryptoConfig.java` (lê `app.draw.encryption-key` em Base64 e **impede a aplicação de subir** se a chave estiver ausente ou não tiver 32 bytes, sem logar o valor), fazendo T065 passar
- [X] T076 [P] [US3] Criar a entidade `…/draw/DrawAssignment.java` (`@EmbeddedId (groupId, giverId)`, `byte[] receiverEnc`, `createdAt`; `toString` **sem** `receiverEnc`) e `…/draw/DrawAssignmentRepository.java`, estendendo `org.springframework.data.repository.Repository` (**não** `JpaRepository`), com apenas `saveAll(...)` e `Optional<DrawAssignment> findByIdGroupIdAndIdGiverId(UUID groupId, UUID giverId)` — sem `findAll`, sem busca por grupo
- [X] T077 [P] [US3] Criar os DTOs `…/draw/dto/DrawResult.java` (`groupId`, `status`, `drawnAt`) e `…/draw/dto/SecretFriendResponse.java` (`name`)
- [X] T078 [US3] Implementar `…/draw/DrawService.java` (`@Transactional`: lock `findByIdForUpdate` → verificar membro, organizador, `OPEN` e ≥ 3 → `DerangementGenerator` → cifrar cada par → `saveAll` → `group.markDrawn(clock.instant())` → publicar `GroupDrawnEvent`; nenhum log com IDs de receiver), fazendo T066 passar (depende de T073 a T077)
- [X] T079 [US3] Implementar `…/draw/SecretFriendService.java` (`getMine(groupId, currentUserId)`), fazendo T067 passar (depende de T075 e T076)
- [X] T080 [US3] Implementar `…/draw/DrawController.java` (`POST /api/v1/groups/{groupId}/draw` e `GET /api/v1/groups/{groupId}/my-secret-friend` com `CacheControl.noStore()`, usando sempre `CurrentUser`), fazendo T068, T069 e T070 passar (depende de T078 e T079)
- [X] T081 [US3] Garantir em `…/group/GroupService.java` que `join` respeita `DRAWN` e expor `drawnAt` e `canDraw` no `GroupDetail`; confirmar que `SseEmitterRegistry` envia `group-drawn` após o commit (complementa T057 e T058)
- [X] T082 [P] [US3] Adicionar a `frontend/src/api/groups.ts` as funções `drawGroup(id)` e `getMySecretFriend(id)`, e tratar `group-drawn` em `frontend/src/hooks/useGroupEvents.ts` (invalidar a query e emitir um callback para o aviso)
- [X] T083 [US3] Criar `frontend/src/components/group/DrawButton.tsx` (com o `Modal` de confirmação) e `frontend/src/components/group/SecretFriendCard.tsx` (revelar/ocultar; query com `gcTime: 0`, `staleTime: 0` e sem persistência), fazendo T071 passar (depende de T082)
- [X] T084 [US3] Completar `frontend/src/pages/GroupPage.tsx` com o estado `DRAWN` e a transição ao vivo com aviso, fazendo T072 passar (depende de T083)

**Checkpoint**: MVP completo (US1 a US3). Quickstart, passos 1 a 16 e a verificação de sigilo passam.

---

## Phase 6: User Story 4 — Ajustar participantes antes do sorteio (Priority: P3)

**Goal**: o participante sai e o organizador remove alguém, sempre antes do sorteio.

**Independent Test**: em um grupo `OPEN`, B sai e C é removido; ambos somem da lista de todos e perdem acesso; C é avisado na hora (quickstart, passo 11).

**Depende de**: US2.

### Tests for User Story 4 ⚠️

- [X] T085 [P] [US4] Teste `test/…/group/GroupMembershipServiceTest.java` (Mockito): `leave` de um participante comum remove a linha e publica `ParticipantsChangedEvent`; `leave` do organizador → `409`; `remove` feito por quem não é organizador → `403`; `remove` do próprio organizador → `409`; `remove` de quem não é membro → `404`; `leave` ou `remove` com o grupo `DRAWN` → `409 /problems/group-already-drawn`; `remove` publica `MembershipRevokedEvent` para o removido; as duas operações usam `findByIdForUpdate`
- [X] T086 [P] [US4] Teste de integração `test/…/integration/MembershipFlowIT.java`: B sai → `GET /groups/{id}` de B → `404`; o organizador remove C → C recebe `membership-revoked` no SSE e a conexão é encerrada; C pode entrar de novo com o código enquanto o grupo está `OPEN`; com o grupo já sorteado, sair e remover → `409` (resultado definitivo)
- [X] T087 [P] [US4] Teste `frontend/src/pages/GroupPage.members.test.tsx`: o organizador vê "Remover" em cada participante, exceto em si mesmo, com confirmação; o participante comum vê "Sair do grupo", com confirmação, e ao sair volta para `/`; `membership-revoked` mostra o toast "Você foi removido deste grupo" e navega para `/`; nada disso aparece em `DRAWN`

### Implementation for User Story 4

- [X] T088 [US4] Implementar em `…/group/GroupService.java` (ou extrair para `…/group/GroupMembershipService.java`) os métodos `leave(groupId, currentUserId)` e `remove(groupId, targetUserId, currentUserId)`, fazendo T085 passar
- [X] T089 [US4] Adicionar a `…/group/GroupController.java` as rotas `DELETE /api/v1/groups/{groupId}/participants/me` e `DELETE /api/v1/groups/{groupId}/participants/{userId}` (204), e a `…/events/SseEmitterRegistry.java` o envio de `membership-revoked` só para o removido, seguido de `complete()` do emitter dele, fazendo T086 passar (depende de T088)
- [X] T090 [US4] Adicionar a `frontend/src/api/groups.ts` as funções `leaveGroup` e `removeParticipant`; botões e confirmações em `frontend/src/components/group/ParticipantList.tsx` e `frontend/src/pages/GroupPage.tsx`; tratar `membership-revoked` em `frontend/src/hooks/useGroupEvents.ts`, fazendo T087 passar

**Checkpoint**: todas as histórias funcionam de forma independente.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: documentação, desempenho, segurança e validação final.

- [ ] T091 [P] Criar `…/config/OpenApiConfig.java` (springdoc só no perfil `dev`) e o teste `test/…/integration/OpenApiContractIT.java`, que, no perfil `dev`, compara os paths e métodos gerados em `/v3/api-docs` com [contracts/openapi.yaml](./contracts/openapi.yaml)
- [ ] T092 [P] Teste de desempenho `test/…/integration/DrawPerformanceIT.java`: sortear um grupo de **100** membros leva < 1 s; 100 clientes consultando `GET /groups/{id}` em paralelo respondem todos em ≤ 2 s (SC-008)
- [ ] T093 [P] Revisar os logs: configurar `backend/src/main/resources/logback-spring.xml` (sem log de corpo de requisição e no nível INFO em produção); varrer o código procurando `log.*receiver`, `log.*password` e `log.*code` e corrigir o que aparecer; confirmar que nenhum `toString` de entidade expõe dados sensíveis
- [ ] T094 [P] Revisão de acessibilidade e responsividade em `frontend/src/**`: em 360px nenhuma tela tem rolagem horizontal; foco visível; contraste AA dos tokens do `@theme`; `aria-live` nos toasts; toda ação com loading/sucesso/erro (FR-033 a FR-035)
- [ ] T095 [P] Criar o `README.md` da raiz: o que é o projeto, como subir no WSL (link para o [quickstart.md](./quickstart.md)), variáveis do `.env`, como rodar os testes, o fluxo SDD e o **aviso de backup** da `DRAW_ENCRYPTION_KEY` junto com o banco
- [ ] T096 Rodar `mvn -B verify` (`backend/pom.xml`) e `npm run test:coverage` (`frontend/package.json`) em container (comandos do quickstart) e confirmar cobertura ≥ 80% nos dois (`backend/target/site/jacoco/index.html`, `frontend/coverage/`)
- [ ] T097 Rodar as varreduras locais (Trivy `fs` e `image`, Gitleaks — comandos do quickstart) e corrigir todo achado HIGH/CRITICAL
- [ ] T098 Executar o roteiro completo do [quickstart.md](./quickstart.md) (passos 1 a 16 e a verificação de sigilo) com `docker compose up -d --build` no WSL e num celular real ou na emulação de 360px
- [ ] T099 Configurar no GitHub, conforme o `CONTRIBUTING.md` (feito pelo mantenedor, ou por automação só com a autorização explícita dele): ruleset da `main` com PR obrigatório, todos os jobs de `.github/workflows/ci.yml` (inclusive `aprovacao`) e de `.github/workflows/security.yml` como checks obrigatórios, branch atualizada antes do merge, sem push direto, sem force push e **sem bypass para administradores**; environment `aprovacao-manual` com o mantenedor como revisor obrigatório. Depois, abrir o PR `001-amigo-secreto-sorteio` → `main` e confirmar que o merge fica bloqueado até os checks e a aprovação

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup e **bloqueia todas as histórias**.
- **US1 (Phase 3)**: depende só da Foundational.
- **US2 (Phase 4)**: depende da Foundational. Usa o login da US1 para testes manuais, mas os testes automatizados criam usuários pelo `ApiClient` (T029).
- **US3 (Phase 5)**: depende da **US2** (grupos e membros).
- **US4 (Phase 6)**: depende da **US2**; pode correr em paralelo com a US3 (arquivos de serviço e controller compartilhados exigem coordenação: T088/T089 depois de T081).
- **Polish (Phase 7)**: depende das histórias desejadas.

```text
Setup ─▶ Foundational ─┬─▶ US1 ───────────────────────┐
                       └─▶ US2 ─┬─▶ US3 (MVP completo) ─┼─▶ Polish
                                └─▶ US4 ────────────────┘
```

### Within Each User Story

- Testes primeiro, vendo-os **falhar**.
- Migração → entidades e repositórios → serviços → controllers → frontend (api → hooks → componentes → páginas).
- Commit ao fim de cada tarefa ou grupo lógico (Conventional Commits).

### Parallel Opportunities

- Setup: T003 a T010 e T012 a T015 em paralelo; T011 espera T009 e T010.
- Foundational: os testes T016 a T020 em paralelo; depois T023 a T026 e T029 a T031 em paralelo.
- Em cada história, todas as tarefas de teste marcadas [P] podem ser escritas em paralelo; backend e frontend da mesma história avançam em paralelo depois que o contrato estiver estável.
- US3 e US4 podem andar em paralelo depois da US2.

---

## Parallel Example: User Story 3

```bash
# Testes críticos primeiro, em paralelo:
Task: "T064 DerangementGeneratorTest (propriedade + uniformidade)"
Task: "T065 AssignmentCipherTest"
Task: "T066 DrawServiceTest"
Task: "T070 SecrecyIT (sigilo ponta a ponta)"
Task: "T071 DrawButton/SecretFriendCard tests"

# Depois, as peças independentes em paralelo:
Task: "T074 DerangementGenerator"
Task: "T075 AssignmentCipher + CryptoConfig"
Task: "T076 DrawAssignment + repositório restrito"
Task: "T077 DTOs do sorteio"
```

---

## Implementation Strategy

### MVP

O produto só tem valor com o sorteio, então o **MVP = Setup + Foundational + US1 + US2 + US3**
(as três histórias P1). A US4 (P3) é um incremento posterior.

1. Setup → Foundational → validar 401/403 e o CI verde.
2. US1 → **parar e validar** (login funcionando no celular).
3. US2 → **parar e validar** (grupo e lista ao vivo com 3 dispositivos).
4. US3 → **parar e validar** (sorteio, revelação e verificação de sigilo) → **MVP pronto**.
5. US4 → validar → Polish → PR para a `main`.

### Sugestão de entregas (PRs menores)

| PR | Conteúdo |
|----|----------|
| 1 | Phase 1 + Phase 2 (infraestrutura, CI e segurança base) |
| 2 | US1 |
| 3 | US2 |
| 4 | US3 |
| 5 | US4 + Polish |

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- [USn] mapeia a tarefa para a história da [spec.md](./spec.md).
- Todo teste deve falhar antes da implementação correspondente.
- Revisões de PR conferem os Princípios I a VIII da constituição.
- Evitar: tarefas vagas, dois agentes no mesmo arquivo ao mesmo tempo e qualquer atalho que exponha pares.
