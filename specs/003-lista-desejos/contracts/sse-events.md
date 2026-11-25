# Contrato: eventos SSE — feature 003

**Nenhum evento novo ou alterado.** Os eventos continuam os das features
[001](../../001-amigo-secreto-sorteio/contracts/sse-events.md) e
[002](../../002-grupo-evento-confirmacao/contracts/sse-events.md).

- **Lista de desejos**: incluir, editar ou remover itens **não** publica sinal. Quem tirou o dono
  vê a versão nova ao revelar de novo, com "Atualizada em dd/mm" (FR-015, FR-015a). Um sinal só
  para quem tirou o dono dependeria de quem tirou quem; um sinal para todos não teria utilidade.
- **Valor do presente**: muda pelo `PUT /groups/{groupId}/event-details`, que já publica
  `group-updated` com `confirmationsReset: false` (o valor não reseta confirmações — FR-023).
- **Sorteio desfeito**: o `draw-undone` existente já faz o cliente apagar o nome revelado; a lista
  vem na mesma consulta do nome e some junto (FR-013).

Nenhum evento carrega itens de lista, ids de itens ou a informação de que alguém viu uma lista
(SC-005).
