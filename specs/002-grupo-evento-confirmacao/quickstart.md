# Quickstart: validar a feature 002 (evento, confirmação e finalização)

Como subir, testar e validar ponta a ponta. Pré-requisitos, `.env`, subida, testes, varreduras
e encerramento são **os mesmos** do [quickstart da feature 001](../001-amigo-secreto-sorteio/quickstart.md)
(Docker no WSL, `docker compose up -d --build`, `mvn -B verify` e `npm run test:coverage` em
containers). API em [contracts/openapi.yaml](./contracts/openapi.yaml), eventos em
[contracts/sse-events.md](./contracts/sse-events.md), telas em
[contracts/ui-routes.md](./contracts/ui-routes.md), tabelas em [data-model.md](./data-model.md).

## 1. Migração sobre dados existentes

Antes de subir a versão nova, guarde um banco com grupos da 001 (um aguardando sorteio e um
sorteado). Depois de subir:

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 2;"
```

**Esperado**: `V6` e `V5` com `success = t`. Na interface, o organizador de cada grupo antigo vê
"Informe a data e o local do evento"; no grupo antigo aguardando sorteio, os outros
participantes aparecem como pendentes; o grupo antigo sorteado continua sorteado (FR-034).

## 2. Roteiro de validação manual

Três janelas (A = organizador, B e C = participantes), uma delas a 360px.

| # | Passo | Resultado esperado | Cobre |
|---|-------|--------------------|-------|
| 1 | A cria um grupo sem data ou sem local | o formulário não deixa enviar e mostra o campo obrigatório | FR-002 |
| 2 | A tenta data de ontem; depois hoje com horário que já passou | "A data do evento precisa ser hoje ou no futuro." / "Esse horário já passou." | FR-003, R14 |
| 3 | A cria o grupo com descrição de 2 linhas, data, sem horário, local `<b>casa</b>` | detalhes aparecem; quebra de linha mantida; `<b>` aparece como texto | US1-1, FR-007 |
| 4 | B e C entram pelo código | aparecem como **Pendente**; contador "1 de 3 confirmados" | US2-1, FR-012 |
| 5 | A tenta sortear | botão bloqueado: "Faltam 2 confirmar: B, C" | US2-4, FR-014 |
| 6 | B confirma | A e C veem B como Confirmado em ≤ 5 s | US2-2, SC-004 |
| 7 | A muda o local (B e C com a tela aberta) | B e C voltam a pendente e veem "confirme de novo" | US2-9, FR-015 |
| 8 | C abre o grupo, A muda a data, C toca "Confirmar" sem recarregar | recusado com "Os detalhes mudaram"; C vê a data nova e confirma | FR-035 |
| 9 | A altera só a descrição | confirmações continuam | US2-10 |
| 10 | B e C confirmam; A sorteia | sorteio acontece como na 001 | US2-5 |
| 11 | B toca "Preciso sair do grupo" → "Pedir para sair" | A vê o pedido; B continua revelando o mesmo amigo | US4-1, FR-027 |
| 12 | B cancela; pede de novo; A recusa | B vê "O organizador recusou"; pode pedir de novo | US4-2, US4-3, FR-025 |
| 13 | B pede de novo e **fecha o navegador**; A aprova | A, C: aviso "sorteio desfeito", grupo aguardando sorteio, nome revelado some | US4-4, Edge Case |
| 14 | B entra de novo no sistema | tela inicial: "Você saiu do grupo X"; o grupo não está mais na lista | FR-026 |
| 15 | B tenta entrar de novo com o código | "Você saiu deste grupo depois do sorteio e não pode entrar de novo." | US4-8, FR-030 |
| 16 | A tenta sortear com A e C | bloqueado: mínimo de 3 | US4-6 |
| 17 | D entra, confirma; A sorteia | novo sorteio só entre A, C e D | US4-5, SC-008 |
| 18 | Num grupo sorteado, A muda o horário do evento para daqui a 2 minutos e espera | em até ~1 min após o horário, todos veem "Finalizado"; o grupo vai para a seção Finalizados | US3-1, US3-2, FR-021 |
| 19 | No grupo finalizado: revelar; tentar editar/sair/pedir saída pela API | revela só o próprio; demais `409 group-finished` | US3-3, US3-4, FR-018 |
| 20 | Grupo não sorteado com a data vencida (ajuste no banco, passo 3 abaixo) | continua aguardando sorteio; A vê "A data do evento já passou" | US3-5 |

## 3. Simular a passagem do tempo

Para os passos 18 e 20 sem esperar o dia do evento, ajuste as datas no banco (só em ambiente
local):

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "update secret_group set event_starts_at = now() - interval '2 days', finalizes_at = now() - interval '1 day' where name = 'Grupo de teste';"
```

**Esperado**: se o grupo estiver sorteado, a tarefa agendada o finaliza em até 1 minuto; se
estiver aguardando sorteio, ele continua assim e o sorteio fica bloqueado.

## 4. Verificação de sigilo (obrigatória — Constituição I)

1. Repita a verificação da 001 (aba Rede, eventos SSE sem nomes, pares cifrados, logs limpos).
2. Depois do passo 13, confira que os pares do sorteio desfeito não existem mais:

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "select count(*) from draw_assignment d join secret_group g on g.id = d.group_id where g.status = 'OPEN';"
```

**Esperado**: `0` — grupo aguardando sorteio não tem nenhum par.

3. Nas respostas de aprovar/recusar/pedir saída e nos avisos, nenhum nome tirado aparece
   (FR-032).

## 5. Cuidado operacional: backups e sorteios desfeitos

A aplicação apaga os pares na aprovação e não guarda cópia (research R18). Um backup do banco
feito **antes** da aprovação ainda contém os pares antigos, cifrados. Pela constituição
(v1.2.1, Princípio II), backups MUST ter a retenção mínima necessária e ficar guardados
**separados** da `DRAW_ENCRYPTION_KEY`, para que nem quem administra recupere um sorteio
desfeito.
