# Contrato: telas — mudanças da feature 002

Mesmas rotas da [feature 001](../../001-amigo-secreto-sorteio/contracts/ui-routes.md); nenhuma
rota nova. Valem os mesmos padrões de interface (360px+, alvos de 44px, WCAG AA, estados de
carregamento/sucesso/erro, pt-BR — Constituição VI). Descrição e local sempre como texto
simples, com quebras de linha preservadas (FR-007).

| Rota | Mudança | Requisitos |
|------|---------|-----------|
| `/` | seções **Em andamento** e **Finalizados**; cartões mostram a data do evento; selo "Finalizado"; avisos "Você saiu do grupo X" (pedido aprovado) com botão "Entendi" | FR-004, FR-020, FR-026 |
| `/grupos/novo` | campos descrição (opcional, contador 0/500), data (obrigatória), horário (opcional) e local (obrigatório) | FR-001 a FR-003 |
| `/grupos/:id` | ver abaixo | FR-004 a FR-035 |

## Tela `/grupos/:id`

**Em todos os status**

- Cartão **Detalhes do evento**: data por extenso ("sábado, 20 de dezembro de 2026, às 19:00",
  ou sem horário), local e descrição.
- **Organizador** (status `OPEN` ou `DRAWN`): botão "Editar detalhes" abre o formulário. Com o
  grupo `OPEN`, se data, horário ou local mudarem, o modal avisa antes de salvar: "Todos os
  participantes precisarão confirmar de novo."
- **Organizador, grupo antigo sem detalhes**: aviso no topo "Informe a data e o local do evento"
  com botão para o formulário (FR-034).

**Status `OPEN`**

- Lista de participantes com selo **Confirmado** ou **Pendente** e contador
  "N de M confirmados" (FR-012), atualizados ao vivo.
- **Participante pendente**: cartão em destaque com os detalhes do evento e o botão
  **Confirmar participação**, mais o link "Não vou participar" (sai do grupo, com confirmação).
  Se o servidor responder `details-changed`, a tela recarrega os detalhes e mostra
  "Os detalhes mudaram. Confira e confirme de novo."
- **Participante confirmado**: selo "Você confirmou"; continua podendo sair do grupo.
- **Organizador**: botão "Realizar sorteio" desabilitado com o motivo de `drawBlockedReason`:
  - `participants-pending`: "Falta(m) N confirmar: Ana, Beto" (FR-014);
  - `not-enough-participants`: "São necessários pelo menos 3 participantes";
  - `event-date-passed`: "A data do evento já passou. Atualize a data para sortear." (US3-5);
  - `event-details-required`: "Informe a data e o local do evento para sortear."
- Depois de um sorteio desfeito (`draw-undone`), aviso fixo até o novo sorteio: "Uma pessoa
  saiu e o sorteio foi desfeito."

**Status `DRAWN`**

- Tudo o que a 001 já mostra (cartão "Seu amigo secreto" com Revelar/Ocultar).
- **Participante (não organizador)**:
  - sem pedido: link "Preciso sair do grupo" → modal explicando que o organizador vai decidir e
    que, se aprovar, o sorteio será refeito para todos → "Pedir para sair";
  - com pedido pendente: aviso "Seu pedido de saída está aguardando o organizador" e botão
    "Cancelar pedido";
  - pedido recusado ou cancelado automaticamente (aviso não visto): "O organizador recusou seu
    pedido de saída." / "Seu pedido de saída foi cancelado." com botão "Entendi".
- **Organizador**: seção **Pedidos de saída** (só aparece se houver) com o nome e a data de cada
  pedido e os botões **Aprovar** e **Recusar**, cada um com modal de confirmação. O modal de
  aprovar avisa: "Fulano sairá do grupo, o sorteio será desfeito para todos e você precisará
  sortear de novo." (SC-009: um toque + confirmação).
- Nenhuma tela mostra quem a pessoa que pediu para sair havia tirado (FR-032).

**Status `FINISHED`**

- Selo "Finalizado" com a data; detalhes do evento; cartão "Seu amigo secreto" com Revelar.
- Lista de participantes só com nomes.
- Sem nenhum botão de alteração (editar, entrar, sair, remover, confirmar, sortear, pedir saída)
  (FR-018).

## Transições ao vivo (SSE)

| Sinal | O que a tela faz |
|-------|------------------|
| `group-updated` | recarrega o grupo; se `confirmationsReset` e o usuário não é organizador, mostra o aviso de confirmar de novo |
| `draw-undone` | esconde o nome revelado, limpa o cache do amigo secreto e mostra o aviso de sorteio desfeito |
| `group-finished` | passa para o modo somente leitura e mostra "O evento aconteceu. Este grupo foi finalizado." |
| `membership-revoked` (`exit-approved`) | mostra "Seu pedido de saída foi aprovado" e volta para `/` |
