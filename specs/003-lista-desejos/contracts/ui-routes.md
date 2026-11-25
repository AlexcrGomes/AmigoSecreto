# Contrato: telas — mudanças da feature 003

Mesmas rotas das features [001](../../001-amigo-secreto-sorteio/contracts/ui-routes.md) e
[002](../../002-grupo-evento-confirmacao/contracts/ui-routes.md); nenhuma rota nova. Valem os
mesmos padrões (360px+, alvos de 44px, WCAG AA, estados de carregamento/sucesso/erro, pt-BR —
Constituição VI). Descrição e link dos itens sempre como texto simples (FR-008).

| Rota | Mudança | Requisitos |
|------|---------|-----------|
| `/grupos/novo` | campo opcional "Valor do presente (R$)" | FR-020 |
| `/grupos/:id` | ver abaixo | FR-001 a FR-023 |

## Tela `/grupos/:id`

**Detalhes do evento** (todos os status)

- O cartão mostra "Valor do presente: R$ 80" quando informado.
- No formulário "Editar detalhes", o campo "Valor do presente (R$)" fica editável com o grupo
  `OPEN`; com o grupo `DRAWN`, desabilitado com a explicação "O valor do presente não muda depois
  do sorteio." (FR-022). Alterar o valor não mostra o aviso "Todos os participantes precisarão
  confirmar de novo." (FR-023).

**Minha lista de desejos** (`OPEN`, `DRAWN` e `FINISHED`)

- Cartão "Minha lista de desejos" com o contador "N de 5" e o texto "Só você e quem tirar você
  veem esta lista."
- Cada item: descrição; link como endereço clicável (com o aviso de site externo); botões
  "Editar" e "Remover" (remover com confirmação).
- "Adicionar sugestão" abre o formulário (descrição obrigatória, link opcional); some com 5
  itens, mostrando "A lista tem no máximo 5 sugestões."
- Lista vazia: "Você ainda não sugeriu presentes. Ajude quem tirar você."
- `FINISHED`: somente leitura, sem "Adicionar", "Editar" nem "Remover".
- Nunca mostra se alguém viu a lista (FR-015).

**Seu amigo secreto** (`DRAWN` e `FINISHED`)

- Depois de "Revelar", abaixo do nome: o valor do presente do grupo (se houver) e a lista de
  desejos de quem foi tirado.
- "Atualizada em dd/mm" quando a lista mudou depois do sorteio (FR-015a).
- Lista vazia: "Ainda não há sugestões de presente."
- Link: ao tocar, modal "Você vai abrir um site externo: <endereço>" com "Abrir" e "Cancelar";
  "Abrir" abre em outra aba, sem informar a página de origem (FR-017).
- "Ocultar" esconde o nome e a lista juntos; `draw-undone` também (FR-011, FR-013).

## Transições ao vivo (SSE)

Nenhum sinal novo. Alterar a própria lista não gera sinal (FR-015). Alterar o valor do presente
usa o `group-updated` que o `PUT event-details` já publica (feature 002).
