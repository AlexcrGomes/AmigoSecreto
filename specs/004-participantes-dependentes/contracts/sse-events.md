# Contrato: eventos SSE — feature 004

**Nenhum evento novo ou alterado.** Os eventos continuam os das features
[001](../../001-amigo-secreto-sorteio/contracts/sse-events.md) e
[002](../../002-grupo-evento-confirmacao/contracts/sse-events.md); os dependentes reaproveitam os
sinais que já existem.

| Ação | Sinal publicado (depois do commit) |
|------|------------------------------------|
| adicionar, renomear ou remover dependente | `participants-changed` |
| organizador remove um dependente | `participants-changed` (o dependente não tem conexão, então não há `membership-revoked` para ele) |
| responsável sai ou é removido antes do sorteio | um único `participants-changed`, já com os dependentes fora; `membership-revoked` só para o responsável removido, como na 001 |
| responsável confirma (dependentes juntos) | `group-updated` com `confirmationsReset: false`, como na 002 |
| pedido de saída de dependente criado ou cancelado | `group-updated` |
| saída de dependente aprovada | `draw-undone` (um só), como na 002 |
| saída do responsável aprovada, com dependentes | um único `draw-undone`; `membership-revoked` (`exit-approved`) só para o responsável |
| alterar a lista de desejos do dependente | nenhum (como na 003) |

Nenhum evento carrega quem é responsável de quem além do que a lista de participantes já mostra,
nem qualquer informação de quem tirou quem (Constituição I). O `participantCount` conta os
dependentes.
