# Contrato: telas — mudanças da feature 005

Mesmas rotas, textos e fluxos das features 001–004 (FR-013). Muda a aparência de todas as telas,
a navegação e a disposição de cada tela em dois desenhos (revisão de 2026-11-27, FR-017). Nenhuma
rota de API, schema ou evento SSE muda. Única novidade de endereço: o parâmetro opcional `?aba=` na
tela do grupo.

## Dois desenhos (research R56)

| Tela | Celular (< 768px) | Tablet, iPad e computador (≥ 768px) |
|------|-------------------|-------------------------------------|
| moldura (logado) | cabeçalho compacto de vidro (marca) no topo; `TabBar` fixa embaixo | menu lateral de vidro à esquerda; sem cabeçalho de topo; conteúdo até 72rem |
| `/entrar`, `/cadastro` | marca acima; formulário num cartão de vidro; sem navegação | duas metades: marca em vermelho com dourado à esquerda, formulário à direita; sem navegação |
| `/` (Meus grupos) | título; "Criar grupo" e "Entrar com código" lado a lado; grupos em lista de cartões; seções "Em andamento" e "Finalizados" | título com os dois botões à direita; grupos em grade de 2 (≥ 768px) ou 3 (≥ 1280px) colunas; mesmas seções |
| `/grupos/novo`, `/entrar-no-grupo` | cartão de largura total | cartão centralizado, até 36rem |
| `/grupos/:id` | cabeçalho do grupo + abas "Resumo" · "Pessoas" · "Desejos" | cabeçalho do grupo + duas colunas, sem abas |
| página não encontrada | cartão simples | cartão centralizado |

## Navegação (FR-015, research R49 e R58)

| Largura | Onde | Itens |
|---------|------|-------|
| < 768px | `TabBar` da Glisse fixa embaixo, acima da área segura; some com o teclado aberto | Meus grupos · Criar grupo · Entrar com código · Sair |
| 768–1023px | menu lateral compacto (5rem): ícone + rótulo curto | idem, "Sair" no fim |
| ≥ 1024px | menu lateral completo (16rem): marca, links com ícone e rótulo, nome do usuário e "Sair" no fim | idem |

- Item atual: `/` e `/grupos/:id` → "Meus grupos"; `/grupos/novo` → "Criar grupo";
  `/entrar-no-grupo` → "Entrar com código". No menu lateral, o atual tem fundo vermelho e
  `aria-current="page"`.
- "Sair" abre "Sair da sua conta?" (`ConfirmModal`, botões "Sair" e "Cancelar"); só confirmar sai;
  "Sair" nunca fica marcado.
- Só uma navegação no DOM por vez.
- Os botões "Criar grupo" e "Entrar com código" de "Meus grupos" continuam (FR-013).

## Tela `/grupos/:id`

- **Cabeçalho do grupo** (sempre visível): nome, selo de status em pílula (Aguardando sorteio /
  Sorteado / Finalizado), "Você confirmou" e a data do sorteio ou da finalização.
- **Três partes** (research R57), sempre montadas, cada uma com título próprio:

| Parte | Conteúdo, na ordem de hoje |
|-------|----------------------------|
| Resumo | avisos (sorteio feito, desfeito, finalizado, decisão de saída); "Confirme sua participação"; detalhes do evento (ou "Informe a data e o local"); código + sortear (aberto) **ou** "Seu amigo secreto" + cartões dos dependentes (sorteado/finalizado); pedidos de saída (organizador); "Sair do grupo" ou painel "Preciso sair do grupo" |
| Pessoas | participantes; meus dependentes |
| Desejos | minha lista; listas dos dependentes |

- **Celular**: abas `SegmentedTabs` ("Partes do grupo") logo abaixo do cabeçalho; "Resumo" aberta por
  padrão; `?aba=pessoas` / `?aba=desejos` abre a aba (valor desconhecido → Resumo); trocar de aba
  atualiza o endereço com `replace`; as partes fora da aba têm `hidden`.
- **≥ 768px**: sem abas e sem `hidden`; Resumo na coluna principal; Pessoas e Desejos, nessa ordem,
  na coluna lateral (18rem; 22rem a partir de 1024px).
- Cartões de destaque (`Glass`): "Confirme sua participação", "Seu amigo secreto", "Amigo secreto
  de Lucas", "Informe a data e o local", painel de pedido de saída.
- **Revelar**: o nome entra com transição suave (sem transição com "reduzir movimento"); oculto,
  só os pontos `••••••` existem no DOM (FR-014, research R50).
- **Avisos**: faixas de vidro com ícone; erros sempre com ícone de alerta e texto.
- Janelas (remover, sortear, pedir saída, link externo, editar detalhes): `Modal` de vidro.
