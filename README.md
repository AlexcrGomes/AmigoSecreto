# Amigo Secreto

Sistema web de amigo secreto, feito para o celular. Cada pessoa cria a sua conta, o organizador
cria um grupo com a descrição, a data e o local do evento e compartilha um código, e os
participantes entram com esse código. Cada participante confirma que vai participar; quando
todos confirmaram, o organizador faz o sorteio, e todos são avisados ao mesmo tempo. Cada um vê
**só** quem ele mesmo tirou: ninguém, nem o organizador, consegue ver o par de outra pessoa.

- **Detalhes do evento**: descrição, data, horário (opcional) e local, sempre no horário de
  Brasília. Mudar a data, o horário ou o local antes do sorteio pede uma nova confirmação de todos.
- **Confirmação**: o sorteio só é liberado com pelo menos 3 participantes, todos confirmados, e o
  evento ainda por vir.
- **Sorteio justo**: feito no servidor, com gerador criptográfico. Se alguém tirar a si mesmo, o
  sorteio inteiro é refeito automaticamente. O sorteio é definitivo, com uma única exceção: se um
  participante pedir para sair e o organizador aprovar, os pares são apagados e o organizador
  sorteia de novo entre quem ficou. Quem saiu não pode voltar.
- **Grupo finalizado**: depois do evento, o grupo sorteado vira *finalizado* sozinho, aparece em
  uma seção própria e fica somente para leitura; cada um ainda pode rever quem tirou.
- **Lista de desejos**: em cada grupo, cada participante sugere até 5 presentes (descrição e link
  opcional). Só ele e quem o tirar no sorteio veem a lista — ela aparece junto do nome revelado,
  nunca para o organizador nem para os outros. Os links abrem fora do sistema, com aviso.
- **Valor do presente**: o organizador pode informar quanto cada um gasta, em média; todos veem, e o
  valor fica fixo depois do sorteio.
- **Sigilo**: os pares ficam cifrados no banco (AES-256-GCM) e a única rota que devolve um amigo
  secreto responde sempre para o usuário logado.
- **Tempo real**: participantes, confirmações, sorteio e finalização aparecem sem recarregar a
  página.

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

> ⚠️ **Backup da chave**: guarde uma cópia da `DRAW_ENCRYPTION_KEY` em lugar seguro, **separada**
> dos backups do banco, e mantenha os backups do banco só pelo tempo necessário. Sem a chave
> original, os pares sorteados ficam ilegíveis para sempre — inclusive para quem administra o
> sistema. Separar a chave dos backups impede que um backup antigo recupere um sorteio desfeito
> por uma saída aprovada (constituição v1.2.1, Princípio II).

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
backend/    API Spring Boot (auth, group, draw, exitrequest, wishlist, events) + migrações Flyway
frontend/   SPA React servida pelo nginx, que também faz proxy de /api
db/         imagem do PostgreSQL com correções de segurança
specs/      especificação, plano, contratos e tarefas (SDD)
.github/    CI, varreduras de segurança, Dependabot e template de PR
```

## Como o projeto é desenvolvido (SDD)

O projeto segue *Spec-Driven Development* com o [GitHub Spec Kit](https://github.com/github/spec-kit):
a [constituição](.specify/memory/constitution.md) define os princípios inegociáveis (sigilo,
sorteio justo, testes, segurança), e cada funcionalidade passa por especificação → plano →
tarefas → implementação, em `specs/<NNN-nome>/`. As funcionalidades estão em
[specs/001-amigo-secreto-sorteio](specs/001-amigo-secreto-sorteio/) (contas, grupos e sorteio) e
[specs/002-grupo-evento-confirmacao](specs/002-grupo-evento-confirmacao/) (detalhes do evento,
confirmação, grupo finalizado e saída depois do sorteio) e
[specs/003-lista-desejos](specs/003-lista-desejos/) (lista de desejos e valor do presente).

Toda mudança entra na `main` por Pull Request, com CI e varreduras verdes e aprovação manual — veja o
[CONTRIBUTING.md](CONTRIBUTING.md).
