# Research: Amigo Secreto — Grupos e Sorteio Sigiloso

**Feature**: `001-amigo-secreto-sorteio` | **Data**: 2026-11-12 | **Plan**: [plan.md](./plan.md)

Versões consultadas em 2026-11-12 nos registros oficiais (npm, Maven Central, Docker Hub,
nodejs.org). Todas as versões ficam fixadas em `pom.xml`, `package-lock.json` e nos
`Dockerfile`s; atualizações chegam por PR do Dependabot (Constituição, Stack).

---

## R1. Versões da stack

| Item | Versão fixada | Observação |
|------|---------------|------------|
| Java | **25 LTS** (Temurin 25.0.5) | LTS vigente; virtual threads estáveis |
| Spring Boot | **4.1.2** | Última GA (4.2 ainda em RC). Traz Spring Framework 7, Spring Security 7, Jackson 3 |
| Maven | 3.10.0 (imagem `maven:3.10.0-eclipse-temurin-25`) | build só em container |
| PostgreSQL | **18.7** (`postgres:18.7-alpine3.24`) | 19 ainda em RC |
| Flyway | gerenciado pelo Boot (linha 13.x) | migrações versionadas |
| Testcontainers | **2.0.5** (`testcontainers-postgresql`) | artefatos renomeados na 2.x |
| springdoc-openapi | 3.2.0 | Swagger UI só no perfil `dev` |
| JaCoCo | 0.8.16 | gate de cobertura 80% |
| Spotless | 3.11.0 (palantir-java-format) | formatação Java |
| Node (build) | **24 LTS "Krypton"** (`node:24.22.3-alpine3.24`) | LTS em manutenção até 2028; 26 LTS acabou de entrar |
| React | **19.3** | |
| Vite | **8.3** + `@vitejs/plugin-react` 6.3 | |
| TypeScript | **6.0.3** | **TS 7.0 foi rejeitado**: `typescript-eslint` 8.72 suporta apenas TS `< 6.1` |
| Tailwind CSS | **4.3** via `@tailwindcss/vite` | configuração CSS-first (`@theme`) |
| React Router | 8.5 (modo declarativo/SPA) | |
| TanStack Query | 5.107 | estado de servidor e cache |
| Formulários | react-hook-form 7.92 + zod 4.6 | validação no cliente espelha o backend |
| Testes front | Vitest 5.1, React Testing Library 16.3, MSW | |
| Lint front | ESLint 10 + typescript-eslint 8.72 + Prettier | |
| Servidor web | `nginxinc/nginx-unprivileged` (stable-alpine, fixado por versão no Dockerfile) | roda como não-root |

**Alternativas consideradas**: Java 21 (ainda suportado, mas 25 é a LTS atual); Spring Boot
3.5 (fim do suporte OSS); Node 26 (LTS há poucas semanas — preferimos a linha mais madura).

---

## R2. Autenticação e sessão

**Decision**: sessão no servidor com Spring Security 7 + **Spring Session JDBC** (sessões
guardadas no PostgreSQL), cookie `SESSION` `HttpOnly`, `Secure` (em produção), `SameSite=Lax`.
Proteção CSRF no padrão SPA do Spring Security (cookie `XSRF-TOKEN` legível pelo JS + cabeçalho
`X-XSRF-TOKEN`). Expiração por inatividade: **8 horas**. Login autentica e cria a sessão;
cadastro já autentica o usuário (US1, cenário 1). Troca do ID de sessão no login (proteção
contra *session fixation*, padrão do Spring Security).

**Rationale**: frontend e API ficam na mesma origem (nginx faz proxy de `/api`), então cookie
de sessão é o mecanismo mais simples e mais seguro: nada de token em `localStorage`
(Constituição III), revogação imediata no logout, sem gestão de chaves JWT. Guardar a sessão no
PostgreSQL faz o login sobreviver a reinícios do container e não exige outro serviço.

**Alternatives considered**:
- JWT em `localStorage` — rejeitado pela constituição (exposição a XSS).
- JWT em cookie HttpOnly — sem revogação simples no logout; mais código (refresh, rotação de
  chave) sem benefício para um monólito.
- Sessão em memória — mais simples, mas todos são deslogados a cada deploy/reinício.
- Redis para sessão — serviço extra desnecessário (Constituição VIII).

---

## R3. Hash de senha

**Decision**: `DelegatingPasswordEncoder` com **BCrypt (custo 12)** como padrão. Senha com 8 a
64 caracteres e no máximo 72 bytes em UTF-8 (limite do BCrypt, validado explicitamente).

**Rationale**: nativo do Spring Security, sem dependência extra; o `Delegating` permite migrar
para Argon2 no futuro sem invalidar senhas existentes.

**Alternatives considered**: Argon2id — exige BouncyCastle; ganho marginal para este sistema.

---

## R4. Limitação de tentativas (rate limiting)

**Decision**: Bucket4j (core, em memória) com buckets guardados em cache Caffeine com expiração:

| Ação | Chave | Limite | Resposta |
|------|-------|--------|----------|
| Login com falha | e-mail normalizado + IP | 5 falhas / 15 min | `429` com `Retry-After` |
| Cadastro | IP | 10 / hora | `429` |
| Entrar com código inválido | usuário | 10 falhas / 10 min | `429` |
| Iniciar sorteio | grupo | 5 / min | `429` |

Capacidade e janela de cada política vêm de `app.rate-limit.*` (padrões acima). O perfil `test`
relaxa só o cadastro, porque os testes de integração criam mais de 100 usuários; o limite real é
coberto pelo teste unitário do `RateLimiter`.

**Rationale**: atende FR-004 e FR-013 sem infraestrutura extra. Uma única instância do backend
basta para a escala da spec (R8).

**Alternatives considered**: rate limit no nginx (`limit_req`) — não diferencia sucesso de falha
nem usuário autenticado; Redis + Bucket4j distribuído — prematuro.

---

## R5. Código do grupo

**Decision**: 8 caracteres do alfabeto `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 símbolos, sem
`0 O 1 I L`), gerados com `SecureRandom`, exibidos como `XXXX-XXXX`. Entrada normalizada:
maiúsculas, sem espaços e sem hífen. Unicidade garantida por índice único; em colisão, gera de
novo (até 5 tentativas).

**Rationale**: 31⁸ ≈ 8,5 × 10¹¹ combinações (~39,6 bits). Com o limite de 10 erros a cada 10
minutos por usuário, adivinhar um código é inviável na prática (FR-009). Oito caracteres ainda
são fáceis de digitar no celular.

**Alternatives considered**: UUID (impossível de digitar); 6 caracteres (~29,7 bits, margem
menor); códigos sequenciais (proibidos pela constituição).

---

## R6. Algoritmo do sorteio

**Decision**: **embaralhamento + rejeição**. Embaralha a lista de participantes com
`Collections.shuffle(lista, SecureRandom)` (Fisher–Yates); o participante `i` tira
`embaralhado[i]` da lista original. Se **qualquer** participante tirar a si mesmo, descarta a
tentativa inteira e embaralha de novo. Limite de segurança de 1.000 tentativas; se for atingido
(probabilidade ≈ (1 − 1/e)¹⁰⁰⁰, na prática zero), lança erro e nada é salvo.

O gerador é uma classe de domínio pura (`DerangementGenerator`), sem Spring nem banco, recebe o
`Random` por injeção (testes determinísticos) e é coberta por testes de propriedade.

**Rationale**:
- Implementa literalmente a regra do usuário: "se alguém tirar a si mesmo, refaz o sorteio".
- Gera um *derangement* **uniformemente aleatório** entre todos os possíveis; a média é de
  ~2,7 tentativas (1/e de chance de cada tentativa ser válida), custo desprezível.
- O refazimento acontece em memória, antes de persistir (Constituição II).

**Alternatives considered**:
- Algoritmo de Sattolo (sempre gera um ciclo único, sem refazer) — não é uniforme entre os
  derangements possíveis e cria uma estrutura previsível (uma única corrente).
- Corrigir só quem tirou a si mesmo (trocar com o vizinho) — enviesa o resultado.

---

## R7. Atomicidade, simultaneidade e idempotência do sorteio

**Decision**: o serviço do sorteio roda em uma transação e trava a linha do grupo com
`SELECT … FOR UPDATE` (`PESSIMISTIC_WRITE`). Dentro da transação:
(1) verifica se o usuário é o organizador; (2) verifica `status = OPEN`; (3) verifica ≥ 3
participantes; (4) gera o derangement; (5) grava todos os pares; (6) muda para `status = DRAWN`
e grava `drawn_at`. Entrar, sair e remover participantes também travam a mesma linha, então
ninguém entra nem sai durante o sorteio. Uma segunda chamada encontra `DRAWN` e recebe `409`.
O evento "grupo sorteado" só é publicado **depois do commit** (`@TransactionalEventListener`
com `AFTER_COMMIT`).

O banco também protege: PK `(group_id, giver_id)` impede dois pares para o mesmo participante.

**Rationale**: atende FR-022, FR-025, FR-026 e US3-9 (cliques duplos/concorrência). Uma falha em
qualquer passo faz rollback de tudo (FR-026).

**Alternatives considered**: apenas lock otimista (`@Version`) — funciona, mas exige tratar
retentativas no meio do fluxo; o lock pessimista sobre uma linha é mais simples e o volume é
baixo.

---

## R8. Notificação em tempo real

**Decision**: **Server-Sent Events (SSE)** por grupo: `GET /api/v1/groups/{id}/events`. Os
eventos são **apenas sinais** (`participants-changed`, `group-drawn`) com `groupId`, `status` e
`participantCount`, **sem nomes e sem pares**. Ao receber um sinal, o frontend refaz a consulta
REST, que aplica toda a autorização. Heartbeat a cada 25 s; o `EventSource` reconecta sozinho e,
ao reconectar, o cliente refaz a consulta. Se o SSE falhar, o TanStack Query faz polling a cada
10 s como contingência. Virtual threads ativadas no Spring
(`spring.threads.virtual.enabled=true`) deixam as conexões abertas baratas. O nginx faz proxy
do SSE com `proxy_buffering off`.

**Rationale**: o fluxo é só servidor → cliente; SSE é HTTP comum, usa o mesmo cookie de sessão e
a mesma verificação de membro, e cumpre SC-004 (≤ 5 s). Como os eventos não carregam dados
sensíveis, mesmo um erro no filtro de eventos não vazaria pares (Constituição I).

**Alternatives considered**:
- WebSocket + STOMP — bidirecional, mais dependências e configuração de segurança própria, sem
  necessidade.
- Só polling — simples, mas para ficar abaixo de 5 s precisaria consultar a cada 2–3 s.
- Múltiplas instâncias do backend exigiriam distribuir os eventos (ex.: `LISTEN/NOTIFY` do
  PostgreSQL) — fora do escopo; uma instância atende a escala da spec.

---

## R9. Sigilo dos pares em repouso (decisão exigida pela Constituição I)

**Decision**: a coluna que diz **quem foi tirado** (`receiver`) é **criptografada na aplicação
com AES-256-GCM** (IV aleatório de 96 bits por linha, `group_id` + `giver_id` como dados
autenticados adicionais). A chave vem da variável de ambiente `DRAW_ENCRYPTION_KEY` (32 bytes em
Base64) e nunca é versionada. A criptografia é feita por um serviço explícito
(`AssignmentCipher`) chamado pelo `DrawService` ao gravar e pelo serviço de consulta ao ler —
**não** por um `AttributeConverter` JPA, porque o converter só recebe o valor da coluna e não
teria acesso a `group_id`/`giver_id` para usar como AAD. O `giver_id` fica em claro, porque é a
chave de busca de "meu amigo secreto".

**Consequências aceitas**:
- O banco não consegue garantir com índice que cada pessoa é tirada só uma vez (o valor
  cifrado é diferente a cada linha). Essa garantia fica no domínio: o `DerangementGenerator`
  valida a permutação antes de gravar, e os testes de propriedade cobrem isso (Constituição IV).
- Os dados associados autenticados impedem copiar o valor cifrado de uma linha para outra.
- Quem tem a chave **e** o banco consegue decifrar: a proteção é contra consultas SQL casuais,
  backups vazados e o próprio administrador espiando o banco. Isso importa porque quem hospeda o
  sistema provavelmente também participa do amigo secreto.

**Alternatives considered**:
- Guardar em claro e confiar só no controle de acesso — qualquer `SELECT` revelaria tudo.
- `pgcrypto` no banco — a chave transitaria nas consultas SQL e poderia parar em logs do banco.
- Guardar só um hash — não daria para mostrar o nome ao participante.

---

## R10. Arquitetura de deploy (Docker)

**Decision**: `compose.yaml` com três serviços:

| Serviço | Imagem | Exposição | Healthcheck |
|---------|--------|-----------|-------------|
| `db` | `postgres:18.7-alpine3.24` | só rede interna | `pg_isready` |
| `backend` | multi-stage: `maven:3.10.0-eclipse-temurin-25` → `eclipse-temurin:25-jre-alpine-3.24`, usuário não-root | só rede interna | `/actuator/health` |
| `frontend` | multi-stage: `node:24.22.3-alpine3.24` → `nginx-unprivileged` | **única porta publicada** (`8080`) | `GET /` |

O nginx serve o build estático do React e faz proxy de `/api/` para o backend. Por isso há
**uma única origem**: o CORS fica desligado e os cookies são same-site. Cabeçalhos de segurança
(CSP, HSTS em produção, `X-Content-Type-Options`, `frame-ancestors 'none'`, `Referrer-Policy`)
são configurados no nginx e reforçados pelo Spring Security. Toda a configuração vem de `.env`
(com modelo em `.env.example`).

Docker roda dentro do WSL nesta máquina; todos os comandos são executados no WSL (ver
[quickstart.md](./quickstart.md)).

**Alternatives considered**: servir o front pelo Spring Boot (um contêiner a menos, mas mistura
os builds e perde o cache estático do nginx); `ddl-auto=update` (proibido pela constituição).

---

## R11. Estratégia de testes

**Decision**:

| Camada | Ferramenta | Foco |
|--------|------------|------|
| Domínio (unit) | JUnit 5 + AssertJ | `DerangementGenerator` (propriedade: 10.000 execuções, n = 3..100), `GroupCodeGenerator`, `AssignmentCipher`, regras de estado do grupo |
| Serviço (unit) | JUnit 5 + Mockito | `AuthService`, `GroupService`, `DrawService` (autorização, estados, rate limit) |
| Web/segurança | `@WebMvcTest` + `spring-security-test` | 401/403/404, CSRF, validação, formato de erro |
| Integração | `@SpringBootTest` + Testcontainers (PostgreSQL 18.7) | migrações Flyway, lock do sorteio com chamadas concorrentes, **teste de sigilo**: N usuários, cada um só obtém o próprio par; o organizador não obtém pares alheios; o valor no banco está cifrado |
| Frontend (unit) | Vitest + RTL + MSW | formulários, rotas protegidas, tela do grupo, botão "Revelar", reação a eventos SSE |

Cobertura com JaCoCo (backend) e `@vitest/coverage-v8` (frontend), gate de **80% de linhas**.
Cada regra crítica começa pelo teste (Constituição IV). Os testes E2E com navegador ficam fora
desta feature: o roteiro manual do `quickstart.md` cobre o fluxo ponta a ponta.

---

## R12. CI e varredura de segurança (GitHub Actions)

**Decision**:

- `ci.yml` (PR e push na `main`): backend `mvn verify` (testes, JaCoCo ≥ 80%, Spotless);
  frontend `npm ci`, `lint`, `test --coverage` (≥ 80%), `build`; build das imagens Docker.
- `security.yml` (PR, push na `main` e semanal):
  - **SAST**: CodeQL (`java-kotlin` e `javascript-typescript`).
  - **SCA**: Trivy `fs` (lê `pom.xml` e `package-lock.json`) + `npm audit --audit-level=high`.
  - **Imagens**: Trivy `image` nas 3 imagens.
  - **Segredos**: Gitleaks no histórico do repositório.
  - Severidade `CRITICAL,HIGH` → falha o job (bloqueia o merge).
- Na execução semanal (ou manual), se Trivy, `npm audit` ou Gitleaks reprovarem, o job
  `abrir-issue` abre uma issue com a etiqueta `seguranca` listando as CVEs (pacote, versão
  instalada e versão corrigida), ou comenta na issue que já estiver aberta. Segredos aparecem só
  com a regra e o local, nunca com o valor. Em PR esse job não roda: o check vermelho já bloqueia.
- `dependabot.yml`: Maven, npm, Docker e GitHub Actions, semanal.
- Actions de terceiros fixadas por SHA de commit.
- Proteção da `main` por ruleset: PR obrigatório, todos os jobs do `ci.yml` e do `security.yml`
  como checks obrigatórios, sem push direto e sem bypass para administradores.
- **Aprovação manual com um único mantenedor**: o GitHub não conta a aprovação do autor do PR.
  Por isso o `ci.yml` tem o job `aprovacao`, que depende dos demais jobs e usa o environment
  `aprovacao-manual`, com o mantenedor como revisor obrigatório. Ele também é check obrigatório, e
  todo novo commit exige nova aprovação. Em repositório privado, isso exige GitHub Pro/Team; sem
  isso, a aprovação manual é o próprio merge do mantenedor após a checklist do PR.

**Rationale**: o Trivy cobre SCA e imagens com uma ferramenta só e sem chave de API.
O OWASP Dependency-Check foi descartado porque depende de uma chave da NVD e tem builds lentos.

**Alternatives considered**: Snyk/SonarCloud (exigem conta e token externo); Semgrep (bom, mas o
CodeQL é nativo do GitHub e gratuito para repositórios públicos).
