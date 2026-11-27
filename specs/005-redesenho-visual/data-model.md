# Data Model: Redesenho Visual

**Nenhuma mudança no banco, nas entidades ou na API.** O "modelo" desta feature são os **tokens do
design**: o conjunto único de cores, superfícies, cantos, espaçamentos e sombras (FR-003), definido
em `frontend/src/index.css` e consumido pelo Tailwind, pelos componentes do projeto e pela Glisse
(research R45).

> **Revisão de 2026-11-27** (research R55): um tema só, claro — branco e vermelho com detalhes
> dourados, sem verde. A coluna "Escuro" e as cores festivas verdes da primeira versão saíram.

## 1. Cores (FR-016: branco e vermelho com detalhes dourados)

| Token | Valor | Uso |
|-------|-------|-----|
| `--color-primary` | `#b3121f` | links, texto de destaque, contorno de foco, item atual do menu |
| `--color-primary-hover` | `#8f0e18` | passar o mouse em ações vermelhas |
| `--color-primary-fill` | `#b3121f` | fundo do botão principal (`filled`) e da marca |
| `--color-on-primary` | `#ffffff` | texto sobre `primary-fill` |
| `--color-accent-gold` | `#a16207` | detalhes dourados **com texto ou ícone** (selos, ícones, sucesso) |
| `--color-gold-soft` | `#e9c46a` | dourado **decorativo, sem texto** (filetes, bordas, brilhos) |
| `--color-text` | `#1f1315` | texto principal |
| `--color-text-muted` | `#5b4448` | texto secundário |
| `--color-success` | `#a16207` | sucesso (dourado; sempre com ícone/texto) |
| `--color-danger` | `#be123c` | erro e ações destrutivas (sempre com ícone + texto) |
| `--color-surface` | `#ffffff` | superfície **sólida** (reduzir transparência, sem desfoque) |
| `--color-surface-muted` | `#fdfaf6` | fundo de áreas internas (itens de lista) |
| `--color-glass` | `rgb(255 255 255 / 0.78)` | superfície de **vidro** |
| `--color-glass-edge` | `rgb(255 255 255 / 0.7)` | borda/brilho do vidro |
| `--color-border` | `#eee2d3` | divisórias (branco quente) |

**Contraste calculado** (WCAG 2.1, sobre o vidro composto no pior ponto do fundo, incluindo as
manchas de luz — research R52):

| Par | Pior caso no vidro | Superfície sólida |
|-----|--------------------|-------------------|
| texto | 17,20 | 18,07 |
| texto secundário | 8,45 | 8,88 |
| primária | 6,61 | 6,95 |
| dourado (`accent-gold` / sucesso) | 4,69 | 4,92 |
| erro | 5,98 | 6,29 |
| `on-primary` / `primary-fill` | 6,95 | — |

Todos ≥ 4,5:1. `gold-soft` (1,67:1 no branco) **nunca** leva texto nem é o único sinal de nada.
O teste `contrast.test.ts` refaz essas contas a partir dos tokens.

## 2. Fundo (FR-005)

| Token | Valor |
|-------|-------|
| `--bg-1` → `--bg-2` → `--bg-3` (degradê) | `#ffffff` → `#fffaf3` → `#fdf3e3` |
| `--bg-spot-red` | `rgb(179 18 31 / 0.10)` (mancha desfocada) |
| `--bg-spot-gold` | `rgb(212 160 23 / 0.20)` (duas manchas desfocadas) |

Paradas usadas no teste de contraste: `#ffffff`, `#fffaf3`, `#fdf3e3`, `#f7e7e9` (mancha vermelha
sobre o branco) e `#f5e2ba` (mancha dourada sobre o creme). Texto nunca fica direto sobre o fundo
(research R48).

## 3. Forma e espaço

| Token | Valor | Uso |
|-------|-------|-----|
| `--radius-card` | `1.75rem` | cartões e janelas (cantos generosos do iOS 26) |
| `--radius-control` | `9999px` | botões e campos em pílula, como na Glisse |
| `--radius-field` | `1rem` | campos de várias linhas e blocos internos |
| `--space-page` | `1rem` (celular) / `2rem` (≥ 768px) | margem lateral |
| `--blur-glass` | `24px` | desfoque do vidro fosco |
| `--shadow-glass` | sombra quente suave + brilho interno de 1px | profundidade do vidro |
| `--tabbar-height` | `4.5rem` + área segura | espaço reservado no fim das telas (< 768px) |
| `--sidebar-width` | `5rem` (768–1023px) / `16rem` (≥ 1024px) | menu lateral (research R56) |
| `--content-max` | `72rem` | largura máxima do conteúdo no desenho largo |

Áreas de toque: mínimo 44×44px em todo controle (FR-007).

## 4. Ponte com a Glisse (research R45, R55)

| Variável da Glisse | Aponta para |
|--------------------|-------------|
| `--lg-foreground` | `--color-text` |
| `--lg-muted` | `--color-text-muted` |
| `--lg-glass` | `--color-glass` |
| `--lg-fill` | `--color-primary-fill` |
| `--lg-destructive` | `--color-danger` |
| `--lg-on` | `--color-accent-gold` |
| `--lg-ring` | `--color-primary` |
| `--lg-separator` | `--color-border` |

As demais variáveis da Glisse ficam nos valores claros dela, garantidos por `<html
data-theme="light">` (R55).

## 5. Estados de superfície (FR-008, FR-009)

| Condição | Superfície |
|----------|-----------|
| padrão | vidro (`--color-glass` + `backdrop-filter: blur(var(--blur-glass)) saturate(1.6)`) |
| `prefers-reduced-transparency: reduce` | sólida (`--color-surface`, sem desfoque) |
| `@supports not (backdrop-filter: blur(1px))` | sólida |
| `prefers-reduced-motion: reduce` | sem transições nem lentes animadas |
| `prefers-color-scheme: dark` | **nada muda** (tema único, R55) |
