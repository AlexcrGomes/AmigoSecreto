# Contrato: telas — mudanças da feature 005

Mesmas rotas, textos e fluxos das features 001–004 (FR-013). Muda a aparência de todas as telas
e a navegação. Nenhuma rota de API, schema ou evento SSE muda.

| Rota | Mudança | Requisitos |
|------|---------|-----------|
| todas | fundo festivo (`AppBackground`), componentes no visual de vidro, tema do aparelho | FR-001 a FR-012 |
| `/entrar`, `/cadastro` | cabeçalho só com o nome do app, sem navegação; formulário num cartão de vidro | FR-011, US3-1 |
| `/` (Meus grupos) | cartões de grupo em vidro fosco; seções "Em andamento" e "Finalizados"; aviso de saída aprovada em vidro | US3-2 |
| `/grupos/novo`, `/entrar-no-grupo` | formulários no visual novo (campos em pílula; data/hora com `.lg-input`) | US3-3 |
| `/grupos/:id` | ver abaixo | US2 |

## Navegação (FR-015, research R49)

| Largura | Onde | Itens |
|---------|------|-------|
| < 768px | barra de abas fixa embaixo (`TabBar`), acima da área segura; some com o teclado aberto | Meus grupos · Criar grupo · Entrar com código · Sair |
| ≥ 768px | a mesma `TabBar` no cabeçalho de vidro, no topo, com o nome do app e o do usuário | idem |

- Aba marcada: `/` e `/grupos/:id` → "Meus grupos"; `/grupos/novo` → "Criar grupo";
  `/entrar-no-grupo` → "Entrar com código".
- "Sair" abre "Sair da sua conta?" (`ConfirmModal`, botões "Sair" e "Cancelar"); só confirma sai.
- Os botões "Criar grupo" e "Entrar com código" de "Meus grupos" continuam (FR-013).

## Tela `/grupos/:id` (US2)

- **Cabeçalho do grupo**: nome, selo de status (Aguardando sorteio / Sorteado / Finalizado) em pílula.
- **Detalhes do evento**, **código do grupo**, **sortear**, **minha lista**, **dependentes**,
  **listas dos dependentes**, **participantes**, **pedidos de saída**: cada um num cartão de vidro
  fosco, na mesma ordem de hoje.
- **Confirme sua participação** e **Seu amigo secreto** / **Amigo secreto de Lucas**: cartões de
  destaque (`Glass` da Glisse).
- **Revelar**: o nome entra com transição suave (sem transição com "reduzir movimento"); oculto,
  só os pontos `••••••` existem no DOM (FR-014, research R50).
- **Avisos** (sorteio feito, sorteio desfeito, grupo finalizado, decisão de saída): faixas de vidro
  com ícone; erros sempre com ícone de alerta e texto.
- Janelas (remover, sortear, pedir saída, link externo): `Modal` de vidro.
