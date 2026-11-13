# Contrato: eventos SSE do grupo

**Endpoint**: `GET /api/v1/groups/{groupId}/events` — `Accept: text/event-stream`

- Exige sessão válida e que o usuário **seja membro** do grupo. Caso contrário: `401` ou `404`,
  como no restante da API.
- Os eventos são **sinais**: avisam que algo mudou, mas **não contêm nomes de participantes nem
  pares do sorteio** (Constituição I). Ao receber um sinal, o cliente refaz
  `GET /api/v1/groups/{groupId}` (e, se `DRAWN`, libera o botão "Revelar").
- Uma conexão é encerrada pelo servidor quando o usuário deixa de ser membro (saiu ou foi
  removido). O evento `membership-revoked` é enviado só para esse usuário antes do fechamento.

## Eventos

| `event` | Quando | `data` (JSON) |
|---------|--------|---------------|
| `participants-changed` | alguém entrou, saiu ou foi removido | `{"groupId":"<uuid>","status":"OPEN","participantCount":7}` |
| `group-drawn` | sorteio concluído (publicado **após o commit**) | `{"groupId":"<uuid>","status":"DRAWN","participantCount":7}` |
| `membership-revoked` | o próprio usuário foi removido | `{"groupId":"<uuid>"}` |
| comentário `: ping` | a cada 25 s | — (mantém a conexão viva em proxies) |

Exemplo de fluxo:

```text
event: participants-changed
data: {"groupId":"3f1c…","status":"OPEN","participantCount":4}

: ping

event: group-drawn
data: {"groupId":"3f1c…","status":"DRAWN","participantCount":4}
```

## Comportamento esperado do cliente

1. Abrir o `EventSource` ao entrar na tela do grupo e fechá-lo ao sair da tela.
2. Em `participants-changed` ou `group-drawn`: invalidar a consulta do grupo (TanStack Query).
3. Em `membership-revoked`: mostrar aviso "Você foi removido deste grupo" e voltar para a lista.
4. Em erro ou reconexão: refazer a consulta do grupo; enquanto o SSE estiver indisponível,
   consultar a cada 10 s (contingência).
5. Requisito de latência: participantes conectados veem `DRAWN` em até 5 s (SC-004).

## Requisitos do proxy (nginx)

`proxy_buffering off`, `proxy_cache off`, `proxy_read_timeout` ≥ 1h,
`proxy_http_version 1.1`, cabeçalho `Connection ""`.
