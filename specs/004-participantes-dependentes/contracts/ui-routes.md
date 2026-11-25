# Contrato: telas — mudanças da feature 004

Mesmas rotas das features [001](../../001-amigo-secreto-sorteio/contracts/ui-routes.md),
[002](../../002-grupo-evento-confirmacao/contracts/ui-routes.md) e
[003](../../003-lista-desejos/contracts/ui-routes.md); nenhuma rota nova. Valem os mesmos padrões
(360px+, alvos de 44px, WCAG AA, estados de carregamento/sucesso/erro, pt-BR — Constituição VI).
Nomes de dependentes sempre como texto simples.

| Rota | Mudança | Requisitos |
|------|---------|-----------|
| `/grupos/:id` | ver abaixo | FR-001 a FR-016 |

## Tela `/grupos/:id`

**Participantes** (todos os status)

- Dependente aparece como "Lucas" com "(dependente de Ana)" em texto secundário (FR-005).
- O contador "N de 100" conta os dependentes (FR-004).
- O organizador vê "Remover" também nos dependentes, com o grupo `OPEN` (FR-006); ao remover um
  participante com dependentes, a confirmação avisa: "Os dependentes de Ana (Lucas, Bia) também
  saem do grupo." (FR-016).

**Meus dependentes** (todos os status; só para quem tem conta)

- Texto: "Adicione quem não tem e-mail, como uma criança. Só você verá quem ele tirar."
- `OPEN`: botão "Adicionar dependente" abre o formulário com o campo "Nome" (2 a 80 caracteres);
  some com 5 dependentes ("Você pode adicionar até 5 dependentes."). Com
  `dependentsBlockedReason`, o botão fica desabilitado com o motivo:
  - `group-full`: "O grupo está cheio (100 participantes)."
  - `dependents-limit`: "Você pode adicionar até 5 dependentes."
  - `dependents-locked`: "Depois da saída de um dependente seu, não dá para adicionar outros neste grupo."
- Cada dependente: nome, situação da confirmação ("Confirmado junto com você" / "Pendente — confirme
  a sua participação"), "Renomear" e "Remover" (com confirmação) só em `OPEN`.
- Cada dependente tem o próprio "Lista de desejos de Lucas" (mesmo editor da 003, "N de 5";
  somente leitura em `FINISHED`) (FR-014).
- `DRAWN`: "Pedir saída de Lucas" (com confirmação e o mesmo texto da 002) ou, com pedido
  pendente, "Saída de Lucas pendente" e "Cancelar pedido" (FR-015).

**Confirmação** (`OPEN`)

- Com dependentes, o botão diz "Confirmar participação (você e Lucas)" (FR-013).

**Amigo secreto dos dependentes** (`DRAWN` e `FINISHED`)

- Logo abaixo de "Seu amigo secreto", um cartão "Amigo secreto do Lucas" por dependente, todos
  começando ocultos (US2-1).
- "Revelar" mostra, só naquele cartão, o nome de quem o Lucas tirou, o valor do presente e a lista
  de desejos da pessoa (mesmo visual da 003). "Ocultar" esconde o cartão (US2-2).
- O resultado vem de `GET /my-dependents/secret-friends`, buscado no primeiro "Revelar" e
  descartado quando todos os cartões dos dependentes ficam ocultos; nada vai para armazenamento
  do navegador (FR-010, research R38).

**Sorteio** (organizador, `OPEN`)

- `drawBlockedReason = no-valid-draw`: botão desabilitado com "Com os dependentes deste grupo, não
  há sorteio possível. Convide mais participantes." (FR-009b).

**Pedidos de saída** (organizador, `DRAWN`)

- Pedido de dependente aparece como "Lucas (dependente de Ana)".

**Avisos de decisão**

- A Ana vê "A saída do Lucas do grupo X foi aprovada/recusada." (`subjectName`).

## Transições ao vivo (SSE)

Nenhum sinal novo ([sse-events.md](./sse-events.md)). `participants-changed` atualiza a lista e o
cartão "Meus dependentes"; `draw-undone` apaga também os resultados revelados dos dependentes.
