# Contrato: eventos SSE do grupo — mudanças da feature 002

Mesmo endpoint e mesmas regras da [feature 001](../../001-amigo-secreto-sorteio/contracts/sse-events.md):
`GET /api/v1/groups/{groupId}/events`, só para membros, e os eventos são **sinais sem nomes e
sem pares** (Constituição I). Ao receber um sinal, o cliente refaz `GET /api/v1/groups/{groupId}`.
Todos os eventos são publicados **após o commit** (research R21).

## Eventos novos

| `event` | Quando | `data` (JSON) |
|---------|--------|---------------|
| `group-updated` | detalhes do evento alterados; alguém confirmou; pedido de saída criado, cancelado ou recusado | `{"groupId":"<uuid>","status":"OPEN","participantCount":7,"confirmationsReset":false}` |
| `draw-undone` | saída aprovada: sorteio desfeito, grupo volta a `OPEN` | `{"groupId":"<uuid>","status":"OPEN","participantCount":6}` |
| `group-finished` | grupo finalizado (tarefa agendada ou primeira escrita após o evento) | `{"groupId":"<uuid>","status":"FINISHED","participantCount":7}` |

`confirmationsReset` é `true` só quando a alteração de data, horário ou local voltou as
confirmações a pendente (FR-015).

## Evento alterado

| `event` | Mudança |
|---------|---------|
| `membership-revoked` | ganha `reason`: `"removed"` (removido pelo organizador, como na 001) ou `"exit-approved"` (pedido de saída aprovado). Exemplo: `{"groupId":"<uuid>","reason":"exit-approved"}`. Continua indo só para quem deixou o grupo, antes de as conexões dele serem fechadas. Quem saiu por conta própria antes do sorteio continua sem receber aviso. |

## Comportamento esperado do cliente (além do da 001)

1. `group-updated`: invalidar a consulta do grupo. Se `confirmationsReset` e o usuário não é o
   organizador, mostrar "Os detalhes do evento mudaram. Confira e confirme de novo."
2. `draw-undone`: **apagar da memória e da tela o nome revelado** (estado local e cache do
   `my-secret-friend`), invalidar a consulta do grupo e mostrar "Uma pessoa saiu do grupo e o
   sorteio foi desfeito. O organizador fará um novo sorteio." (Edge Case da spec).
3. `group-finished`: invalidar a consulta do grupo e a lista "Meus grupos"; a tela passa para o
   modo somente leitura.
4. `membership-revoked` com `reason = "exit-approved"`: mostrar "Seu pedido de saída foi aprovado.
   Você saiu do grupo.", marcar o aviso como visto (`POST /exit-request-notices/{id}/ack`, depois
   de buscar a lista) e voltar para a tela inicial.
5. Latência: participantes conectados veem a mudança em até 5 s (SC-004). A finalização pode
   levar até ~1 minuto a mais, pelo intervalo da tarefa agendada (research R17).
