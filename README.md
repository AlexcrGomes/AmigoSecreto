# Amigo Secreto

Sistema web de amigo secreto, feito para o celular. Cada pessoa cria a sua conta, o organizador
cria um grupo e compartilha um código, e os participantes entram com esse código. Quando todos
entraram, o organizador faz o sorteio, e todos são avisados ao mesmo tempo. Cada um vê **só**
quem ele mesmo tirou: ninguém, nem o organizador, consegue ver o par de outra pessoa.

- **Sorteio justo**: feito no servidor, uma única vez, com gerador criptográfico. Se alguém tirar a
  si mesmo, o sorteio inteiro é refeito automaticamente.
- **Sigilo**: os pares ficam cifrados no banco (AES-256-GCM) e a única rota que devolve um amigo
  secreto responde sempre para o usuário logado.
- **Tempo real**: participantes e resultado aparecem sem recarregar a página.

## Tecnologias

| Parte | Stack |
|-------|-------|
| Backend | Java 25, Spring Boot 4.1 (Web MVC, Security, Data JPA, Session JDBC), Flyway, Bucket4j |
| Banco | PostgreSQL 18 |
| Frontend | React 19, Vite 8, TypeScript 6, Tailwind CSS 4, TanStack Query, React Router |
| Testes | JUnit 5, Mockito, Testcontainers, JaCoCo; Vitest, Testing Library, MSW |
| Entrega | Docker Compose (nginx + backend + banco), GitHub Actions (CI e varreduras de segurança) |

## Como subir

Só é preciso **Git e Docker**. Nesta máquina o Docker roda no **WSL**: os comandos abaixo são
executados num terminal WSL, na pasta do projeto. O passo a passo completo, com o roteiro de
validação, está no [quickstart](specs/001-amigo-secreto-sorteio/quickstart.md).

```bash
cp .env.example .env
```

Preencha o `.env` (ele nunca é versionado):

| Variável | O que é |
|----------|---------|
| `POSTGRES_PASSWORD` | senha do banco (`openssl rand -base64 24`) |
| `DRAW_ENCRYPTION_KEY` | chave AES-256 dos pares do sorteio: 32 bytes em Base64 (`openssl rand -base64 32`). **Sem ela o backend não sobe** |
| `APP_PORT` | porta publicada do app (padrão `8080`) |
| `COOKIE_SECURE` | `false` em ambiente local via http; `true` em produção (https) |

```bash
docker compose up -d --build
```

Abra `http://localhost:8080` (no celular, na mesma rede: `http://<ip-da-máquina>:8080`).

> ⚠️ **Backup da chave**: guarde a `DRAW_ENCRYPTION_KEY` junto com o backup do banco. Sem a chave
> original, os pares sorteados ficam ilegíveis para sempre — inclusive para quem administra o sistema.

Em produção, sirva o app por **HTTPS** (proxy com TLS na frente do nginx) e use `COOKIE_SECURE=true`.

## Como rodar os testes

Tudo em containers, sem JDK nem Node na máquina:

```bash
docker run --rm --network host -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/repo -v "$HOME/.m2":/root/.m2 -w /repo/backend maven:3.10.0-eclipse-temurin-25 mvn -B verify
```

```bash
docker run --rm -v "$PWD/frontend":/app -w /app node:24.22.3-alpine3.24 sh -c "npm ci && npm run lint && npm run format:check && npm run test:coverage"
```

O build falha com cobertura de linhas abaixo de 80% (JaCoCo no backend, Vitest no frontend).

## Estrutura

```text
backend/    API Spring Boot (auth, group, draw, events) + migrações Flyway
frontend/   SPA React servida pelo nginx, que também faz proxy de /api
db/         imagem do PostgreSQL com correções de segurança
specs/      especificação, plano, contratos e tarefas (SDD)
.github/    CI, varreduras de segurança, Dependabot e template de PR
```

## Como o projeto é desenvolvido (SDD)

O projeto segue *Spec-Driven Development* com o [GitHub Spec Kit](https://github.com/github/spec-kit):
a [constituição](.specify/memory/constitution.md) define os princípios inegociáveis (sigilo,
sorteio justo, testes, segurança), e cada funcionalidade passa por especificação → plano →
tarefas → implementação, em `specs/<NNN-nome>/`. A primeira funcionalidade está em
[specs/001-amigo-secreto-sorteio](specs/001-amigo-secreto-sorteio/).

Toda mudança entra na `main` por Pull Request, com CI e varreduras verdes e aprovação manual — veja o
[CONTRIBUTING.md](CONTRIBUTING.md).
