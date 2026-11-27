# Quickstart: validar a feature 004 (participantes dependentes)

Pré-requisitos, `.env`, subida, testes e encerramento são **os mesmos** dos quickstarts das
features [001](../001-amigo-secreto-sorteio/quickstart.md),
[002](../002-grupo-evento-confirmacao/quickstart.md) e [003](../003-lista-desejos/quickstart.md)
(Docker no WSL, `docker compose up -d --build`, `mvn -B verify` e `npm run test:coverage` em
containers). API em [contracts/openapi.yaml](./contracts/openapi.yaml), telas em
[contracts/ui-routes.md](./contracts/ui-routes.md), tabelas em [data-model.md](./data-model.md).

> Dica: o WSL desliga quando fica ocioso e reinicia os containers. Durante o roteiro, mantenha
> `wsl -e sleep 7200` em segundo plano.

## 1. Migração

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "select version, success from flyway_schema_history order by installed_rank desc limit 1;"
docker compose exec db psql -U amigo -d amigosecreto -c "select kind, count(*) from app_user group by kind;"
```

**Esperado**: `V8` com `success = t`; todas as contas existentes como `ACCOUNT`; nenhum grupo muda.

## 2. Roteiro de validação manual

Quatro sessões: A = organizador, B = Ana (responsável), C = Bruno, D = Carla; uma a 360px.

| # | Passo | Resultado esperado | Cobre |
|---|-------|--------------------|-------|
| 1 | A cria o grupo; B entra e adiciona "Lucas" | todos veem "Lucas (dependente de Ana)"; contador sobe | US1-1, FR-005 |
| 2 | B tenta nome vazio e "L" | recusado com a mensagem do campo | US1-2 |
| 3 | B renomeia para "Lucas M." e depois adiciona "Bia" | lista atualizada ao vivo para todos | US1-4 |
| 4 | C tenta, pela API, `PUT`/`DELETE /my-dependents/{id do Lucas}` | `404` | US1-5, SC-004 |
| 5 | B remove a Bia por um instante: só A, B e Lucas no grupo (B e Lucas só poderiam tirar A); A tenta sortear. Depois B adiciona a Bia de novo | "Com os dependentes deste grupo, não há sorteio possível…" antes de tentar. Com A, B, Lucas e Bia o sorteio **é** possível (A↔B, Lucas↔Bia) | FR-009b |
| 6 | C e D entram e confirmam; B confirma | botão "Confirmar participação (você, Lucas M. e Bia)"; Lucas e Bia confirmados junto | US3-1, FR-013 |
| 7 | A muda a data do evento | B, Lucas e Bia voltam a pendente; B confirma e os três voltam juntos | US3-2 |
| 8 | B monta a lista de desejos do Lucas (2 itens) | "2 de 5"; só B edita | US3-3, FR-014 |
| 9 | B adiciona até 5 dependentes e tenta o 6º | "Você pode adicionar até 5 dependentes." (depois remova os extras) | FR-003 |
| 10 | A sorteia | sorteio feito; B nunca tira Lucas nem Bia, e nenhum deles tira B (repita com um grupo descartável algumas vezes) | FR-009a |
| 11 | B abre o grupo | "Seu amigo secreto", "Amigo secreto do Lucas M." e "Amigo secreto da Bia", todos ocultos; revelar um não revela os outros | US2-1, US2-2 |
| 12 | A, C e D procuram o resultado do Lucas (tela, Rede, `GET /my-dependents/secret-friends`) | nunca aparece; a rota devolve `[]` para eles | US2-3, SC-002 |
| 13 | Quem tirou o Lucas revela | vê "Lucas M." e os 2 itens da lista dele | US2-4 |
| 14 | B pede a saída do Lucas; A vê "Lucas M. (dependente de Ana)" e aprova | sorteio desfeito; B recebe "A saída do Lucas M. … foi aprovada" | US3-4, FR-015 |
| 15 | B tenta adicionar outro dependente | desabilitado: "Depois da saída de um dependente seu, não dá para adicionar outros neste grupo."; C ainda consegue adicionar um | Clarifications Q3 |
| 16 | Antes do novo sorteio, A remove B | B e Bia saem juntos; a lista avisa antes de confirmar | US3-5, FR-016 |
| 17 | Grupo finalizado (simular como no quickstart da 002, §3) com um responsável e dependente | o responsável ainda revela o resultado do dependente; lista do dependente somente leitura | US2-5 |

## 3. Verificação de sigilo (obrigatória — Constituição I)

1. Repita a verificação das features 001–003 (aba Rede, eventos SSE sem nomes, pares cifrados,
   logs limpos).
2. Na aba Rede de A, C e D: `GET /groups/{id}` traz em `myDependents` só os próprios (vazio) e
   nenhum resultado de dependente; `GET /my-dependents/secret-friends` responde `[]`.
3. Na aba Rede de B: `my-dependents/secret-friends` não traz o id de quem foi tirado.
4. Um dependente nunca autentica:

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "select count(*) from app_user where kind = 'DEPENDENT' and (email is not null or password_hash is not null);"
```

**Esperado**: `0`.

5. Nenhuma participação de dependente sobra sem responsável, e nenhum dependente sobra sem
   participação (salvo os referenciados por pedido de saída):

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "select count(*) from app_user u where u.kind = 'DEPENDENT' and not exists (select 1 from group_member m where m.user_id = u.id) and not exists (select 1 from exit_request e where e.user_id = u.id);"
```

**Esperado**: `0`.
