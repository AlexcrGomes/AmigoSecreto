# Data Model: Redesenho Visual

**Nenhuma mudança no banco, nas entidades ou na API.** O "modelo" desta feature são os **tokens do
design**: o conjunto único de cores, superfícies, cantos, espaçamentos e sombras (FR-003), definido
em `frontend/src/index.css` e consumido pelo Tailwind, pelos componentes do projeto e pela Glisse
(research R45).

## 1. Cores (FR-016: festivo — vermelho, verde e dourado)

| Token | Claro | Escuro | Uso |
|-------|-------|--------|-----|
| `--color-primary` | `#b3121f` | `#ff6b6b` | links, texto de destaque, contorno de foco |
| `--color-primary-fill` | `#b3121f` | `#c81e2b` | fundo do botão principal (`filled`) |
| `--color-on-primary` | `#ffffff` | `#ffffff` | texto sobre `primary-fill` |
| `--color-accent-green` | `#15803d` | `#4ade80` | detalhes festivos, switch ligado |
| `--color-accent-gold` | `#92400e` | `#fbbf24` | detalhes festivos (selos, brilhos) |
| `--color-text` | `#1f1315` | `#fff7f5` | texto principal |
| `--color-text-muted` | `#5b4448` | `#e7cfd2` | texto secundário |
| `--color-success` | `#15803d` | `#4ade80` | sucesso (sempre com ícone/texto) |
| `--color-danger` | `#be123c` | `#fb7185` | erro e ações destrutivas (sempre com ícone + texto) |
| `--color-surface` | `#ffffff` | `#1c1416` | superfície **sólida** (reduzir transparência, sem desfoque) |
| `--color-glass` | `rgb(255 255 255 / 0.72)` | `rgb(28 20 22 / 0.66)` | superfície de **vidro** |
| `--color-glass-edge` | `rgb(255 255 255 / 0.6)` | `rgb(255 255 255 / 0.12)` | borda/brilho do vidro |
| `--color-border` | `#e7d6d8` | `rgb(255 255 255 / 0.14)` | divisórias |

**Contraste calculado** (WCAG 2.1, sobre o vidro composto no pior fundo do degradê — research R52):

| Par | Claro (pior caso) | Escuro (pior caso) |
|-----|-------------------|--------------------|
| texto / vidro | 16,95 | 14,62 |
| texto secundário / vidro | 8,33 | 10,48 |
| primária / vidro | 6,52 | 5,57 |
| sucesso / vidro | 4,70 | 8,86 |
| erro / vidro | 5,89 | 5,74 |
| dourado / vidro | 6,65 | 9,25 |
| `on-primary` / `primary-fill` | 6,95 | 5,71 |

Todos ≥ 4,5:1. O teste `contrast.test.ts` refaz essas contas a partir dos tokens.

## 2. Fundo (FR-005)

| Token | Claro | Escuro |
|-------|-------|--------|
| `--bg-base` (degradê) | `#fff7ed` → `#fde2e4` → `#fef3c7` | `#1c0a10` → `#4c0519` → `#064e3b` |
| manchas de luz | vermelho, verde e dourado em 30–45% de opacidade, desfocadas | as mesmas em 25–35% |

Texto nunca fica direto sobre o fundo (research R48).

## 3. Forma e espaço

| Token | Valor | Uso |
|-------|-------|-----|
| `--radius-card` | `1.75rem` | cartões e janelas (cantos generosos do iOS 26) |
| `--radius-control` | `9999px` | botões e campos em pílula, como na Glisse |
| `--radius-field` | `1rem` | campos de várias linhas e seletores nativos |
| `--space-page` | `1rem` | margem lateral a 360px |
| `--blur-glass` | `24px` | desfoque do vidro fosco |
| `--shadow-glass` | sombra suave + brilho interno de 1px | profundidade do vidro |
| `--tabbar-height` | `4.5rem` + área segura | espaço reservado no fim das telas (< 768px) |

Áreas de toque: mínimo 44×44px em todo controle (FR-007).

## 4. Ponte com a Glisse (research R45)

| Variável da Glisse | Aponta para |
|--------------------|-------------|
| `--lg-foreground` | `--color-text` |
| `--lg-muted` | `--color-text-muted` |
| `--lg-glass` | `--color-glass` |
| `--lg-fill` | `--color-primary-fill` |
| `--lg-destructive` | `--color-danger` |
| `--lg-on` | `--color-accent-green` |
| `--lg-ring` | `--color-primary` |
| `--lg-separator` | `--color-border` |

## 5. Estados de superfície (FR-008, FR-009)

| Condição | Superfície |
|----------|-----------|
| padrão | vidro (`--color-glass` + `backdrop-filter: blur(var(--blur-glass)) saturate(1.6)`) |
| `prefers-reduced-transparency: reduce` | sólida (`--color-surface`, sem desfoque) |
| `@supports not (backdrop-filter: blur(1px))` | sólida |
| `prefers-reduced-motion: reduce` | sem transições nem lentes animadas |
| `prefers-color-scheme: dark` | todos os tokens na coluna "Escuro" |
