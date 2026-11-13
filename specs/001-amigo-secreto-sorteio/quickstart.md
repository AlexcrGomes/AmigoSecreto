# Quickstart: validar a feature 001 (Amigo Secreto)

Guia para subir o sistema e provar, ponta a ponta, que a feature funciona. Os detalhes da API
estão em [contracts/openapi.yaml](./contracts/openapi.yaml), os eventos em
[contracts/sse-events.md](./contracts/sse-events.md), as telas em
[contracts/ui-routes.md](./contracts/ui-routes.md) e as tabelas em
[data-model.md](./data-model.md).

## Pré-requisitos

- Git e Docker (Engine 29+ com Compose v2). **Nesta máquina o Docker roda no WSL**, então todos
  os comandos abaixo são executados num terminal WSL, na pasta do projeto:

```bash
cd "/mnt/c/Users/alex.gomes/Documents/Alex projetos/amigoSecreto"
```

- Não é preciso ter JDK nem Node instalados: o build acontece nos containers (Constituição VII).

## 1. Configurar o ambiente

```bash
cp .env.example .env
```

Preencha no `.env` (o arquivo nunca é commitado):

| Variável | Como gerar |
|----------|------------|
| `POSTGRES_PASSWORD` | `openssl rand -base64 24` |
| `DRAW_ENCRYPTION_KEY` | `openssl rand -base64 32` (chave AES-256 dos pares — research R9) |
| `APP_PORT` | porta publicada do frontend (padrão `8080`) |
| `COOKIE_SECURE` | `false` em ambiente local via http; `true` em produção (https) |

## 2. Subir o sistema

```bash
docker compose up -d --build
```

```bash
docker compose ps
```

**Esperado**: `db`, `backend` e `frontend` com estado `healthy`. Só a porta do frontend está
publicada no host. Abra `http://localhost:8080` (no celular: `http://<ip-da-máquina>:8080`).

## 3. Rodar os testes

Backend (unitários + integração com Testcontainers + cobertura + formatação), num container
Maven com acesso ao Docker do WSL:

```bash
docker run --rm --network host -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD/backend":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.10.0-eclipse-temurin-25 mvn -B verify
```

Frontend (lint + formatação + testes + cobertura):

```bash
docker run --rm -v "$PWD/frontend":/app -w /app node:24.22.3-alpine3.24 sh -c "npm ci && npm run lint && npm run format:check && npm run test:coverage"
```

**Esperado**: tudo verde e cobertura ≥ 80% nos dois (o build falha abaixo disso). O relatório
do JaCoCo fica em `backend/target/site/jacoco/index.html`.

## 4. Roteiro de validação manual (cenários da spec)

Use três navegadores ou janelas anônimas (A = organizador, B e C = participantes), de
preferência com uma delas no celular ou no modo responsivo de 360px.

| # | Passo | Resultado esperado | Cobre |
|---|-------|--------------------|-------|
| 1 | A acessa `/` sem login | redireciona para `/entrar`; não há "esqueci minha senha" | FR-006, FR-007 |
| 2 | A cria a conta | entra direto em "Meus grupos" (vazio) | US1-1 |
| 3 | Criar outra conta com o mesmo e-mail | erro "e-mail já cadastrado" | US1-2 |
| 4 | Login com senha errada 6 vezes | mensagem genérica; na 6ª, bloqueio temporário | US1-4, FR-004 |
| 5 | A cria o grupo "Natal 2026" | código `XXXX-XXXX` com Copiar e Compartilhar | US2-1 |
| 6 | A tenta sortear com 1 participante | botão desabilitado com a dica de mínimo de 3 | US3-1 |
| 7 | B entra com o código em minúsculas | entra; a lista de A atualiza sozinha em ≤ 5 s | US2-2, US2-5 |
| 8 | B informa o código de novo | continua com um único registro na lista | US2-4 |
| 9 | C informa um código errado | "código inválido"; após 10 erros, bloqueio | US2-3, FR-013 |
| 10 | C entra; B não vê botão de sortear | só A vê "Realizar sorteio" | US3-2 |
| 11 | A remove C e C entra de novo; B sai e entra de novo | as listas atualizam; C é avisado da remoção | US4 |
| 12 | Com A, B e C nas telas do grupo, A sorteia e confirma | as três telas mudam para "Sorteado" em ≤ 5 s | US3-3, US3-5, SC-004 |
| 13 | Cada um toca em "Revelar" | cada um vê um nome diferente do próprio; os 3 nomes formam a troca completa | US3-3, US3-6 |
| 14 | A tenta sortear de novo (inclusive pela API) | `409` — resultado definitivo | US3-9, FR-025 |
| 15 | D (nova conta) entra com o código | "o sorteio já foi realizado" | US3-8 |
| 16 | D abre `/grupos/<id>` direto | "grupo não encontrado", sem nenhum dado | FR-017 |

### Verificação de sigilo (obrigatória — Constituição I)

1. Nas ferramentas do navegador (aba Rede) de A, confira que **nenhuma** resposta além de
   `my-secret-friend` contém o nome tirado, e que os eventos SSE não trazem nomes.
2. Confira que a resposta de `POST /draw` não contém pares.
3. No banco, os pares estão cifrados:

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "select group_id, giver_id, encode(substring(receiver_enc from 1 for 8),'hex') as inicio_cifrado from draw_assignment limit 3;"
```

**Esperado**: só bytes cifrados, nenhum UUID de quem foi tirado em claro.

4. Nos logs, nenhum par ou nome sorteado aparece:

```bash
docker compose logs backend | grep -i -E "receiver|secret-friend|tirou" || echo "OK: nada encontrado"
```

## 5. Varreduras de segurança locais (opcional; o CI roda sempre)

```bash
docker run --rm -v "$PWD":/src aquasec/trivy:0.76.2 fs --scanners vuln,secret --severity HIGH,CRITICAL --exit-code 1 /src
```

```bash
docker run --rm -v "$PWD":/repo zricethezav/gitleaks:v8.30.1 detect --source /repo
```

**Esperado**: sem achados Altos ou Críticos e sem segredos.

## 6. Encerrar

```bash
docker compose down
```

Para apagar também os dados do banco, use `docker compose down -v`.
