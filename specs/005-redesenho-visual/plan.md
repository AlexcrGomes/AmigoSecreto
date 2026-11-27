# Implementation Plan: Redesenho Visual

**Branch**: `005-redesenho-visual` | **Date**: 2026-11-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-redesenho-visual/spec.md`

## Summary

Todas as telas ganham um visual novo no estilo "Liquid Glass" do iOS 26, com a biblioteca Glisse
onde ela tem componentes e componentes do projeto no mesmo estilo onde não tem; tema claro/escuro
do aparelho; paleta festiva (vermelho, verde, dourado) sobre um fundo em degradê; navegação por abas
embaixo no celular e no topo a partir de 768px, com confirmação ao sair. Nenhuma regra, texto, rota
ou API muda.

Abordagem técnica: **só frontend**. A Glisse entra como dependência fixada (R43), isolada atrás de
`components/ui/*`, que mantêm a API de hoje (R46) — as páginas mudam pouco e os testes de
comportamento continuam valendo. O CSS da Glisse é empacotado pelo Vite e a injeção em tempo de
execução é desligada por um marcador, sem afrouxar a CSP (R44). Um único conjunto de tokens
alimenta Tailwind e Glisse (R45). Vidro completo, fosco ou sólido conforme o navegador e as
preferências de acessibilidade (R47). Decisões em [research.md](./research.md) (R43–R54).

## Technical Context

**Language/Version**: TypeScript 6.0 (frontend) — sem mudança; backend intocado

**Primary Dependencies**: React 19.3, Vite 8.3, Tailwind 4.3, React Router, TanStack Query 5,
react-hook-form + zod 4 (sem mudança). **Nova: `glisse` 0.6.0** (versão exata; MIT; só
`react`/`react-dom` como peer) — justificada em R43, por pedido explícito do usuário

**Storage**: N/A (nenhuma mudança de banco)

**Testing**: Vitest + React Testing Library + MSW (stubs de canvas/`ResizeObserver`/`matchMedia`
para a Glisse no jsdom, R53); teste de contraste dos tokens sem dependência nova (R52); roteiro
manual nos dois temas, a 360px e a 1280px ([quickstart.md](./quickstart.md))

**Target Platform**: navegadores móveis e desktop (Chrome/Edge, Safari/iOS, Firefox); containers
como hoje

**Project Type**: aplicação web — só o `frontend/` muda

**Performance Goals**: primeira tela utilizável ≤ 3 s em celular intermediário com 4G (SC-004);
rolagem sem engasgos com o vidro (lentes `Glass` limitadas a TabBar, janelas e 2 cartões de
destaque, R47); +~40 KB compactados de JS/CSS da Glisse

**Constraints**: CSP atual intacta (`style-src 'self'`, R44); contraste WCAG AA nos dois temas
inclusive sobre vidro (FR-006); 360px sem rolagem horizontal e toque ≥ 44px (FR-007); textos,
rotas e fluxos iguais (FR-013); sigilo da revelação (FR-014)

**Scale/Scope**: 7 telas, ~30 componentes; 12 componentes de `ui/` redesenhados ou novos
([contracts/ui-components.md](./contracts/ui-components.md)); 0 rotas de API

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição **v1.3.0**.

| # | Princípio | Como o plano cumpre | Pré-pesquisa | Pós-design |
|---|-----------|---------------------|:---:|:---:|
| I | Sigilo (não negociável) | Nenhuma rota ou dado novo; a revelação continua sem o nome no DOM enquanto oculto — nenhum efeito visual pode deixá-lo legível (R50); nada no armazenamento do navegador | ✅ | ✅ |
| II | Sorteio justo (não negociável) | Intocado | ✅ | ✅ |
| III | Segurança por padrão | CSP mantida sem `unsafe-inline` (R44); Glisse sem dependências transitivas, versão exata, `npm audit` e Trivy no CI (R43) | ✅ | ✅ |
| IV | Testes obrigatórios (não negociável) | Testes dos componentes base e da navegação antes da troca; contraste testado (R52); testes de comportamento existentes continuam passando; gate de 80% (R53) | ✅ | ✅ |
| V | Varredura contínua | Sem mudança; a nova dependência entra nas varreduras | ✅ | ✅ |
| VI | Mobile-first e moderno | 360px, 44px, AA nos dois temas, rótulos e erros, estados de ação (FR-006 a FR-012). **Tailwind**: o estilo do projeto continua em Tailwind com tokens únicos (R45); o CSS da Glisse é o CSS de uma biblioteca de componentes, não CSS ad hoc do projeto, e é ajustado só por variáveis — ver nota abaixo | ✅ | ✅ |
| VII | Containerização | Sem mudança nos containers; build do Vite já empacota o CSS | ✅ | ✅ |
| VIII | Simplicidade | Dependência nova justificada no plano (R43), isolada em `components/ui/*`; ícones próprios em vez de biblioteca (R51); sem dependência para acessibilidade (R52) | ✅ | ✅ |
| — | Stack obrigatória | Inalterada (a Glisse é uma biblioteca dentro do React SPA, não troca a stack) | ✅ | ✅ |

**Nota sobre o Princípio VI**: a regra "CSS ad hoc só quando o Tailwind não atender" trata do CSS
escrito pelo projeto. O projeto continua escrevendo estilos em Tailwind; o único CSS próprio fora
de utilitários é o que o Tailwind não expressa: as variáveis-ponte `--lg-*` (R45), o fundo com
manchas desfocadas (R48) e os `@media (prefers-reduced-transparency)`/`@supports` (R47) — todos em
`index.css`, ao lado do `@theme`. Não há conflito; **nenhuma emenda é necessária**.

**Resultado**: nenhum desvio. *Complexity Tracking* não se aplica.

## Project Structure

### Documentation (this feature)

```text
specs/005-redesenho-visual/
├── plan.md              # Este arquivo
├── research.md          # Fase 0: decisões R43–R54
├── data-model.md        # Fase 1: tokens do design (sem mudança de banco)
├── quickstart.md        # Fase 1: roteiro visual, contraste, 360px, desempenho
├── contracts/
│   ├── ui-components.md # componentes de ui/ e o que vem da Glisse
│   └── ui-routes.md     # mudanças por tela e navegação
├── checklists/
│   └── requirements.md
└── tasks.md             # Fase 2 (/speckit-tasks — ainda não criado)
```

### Source Code (repository root)

Só o `frontend/` muda. Cria (➕) ou altera (✏️):

```text
frontend/
├── package.json / package-lock.json        ✏️ glisse 0.6.0 (exata)
├── index.html                               ✏️ <meta id="glisse-styles">, viewport-fit=cover, theme-color por tema
└── src/
    ├── main.tsx                             ✏️ import 'glisse/styles.css' antes de index.css
    ├── index.css                            ✏️ tokens festivos claro/escuro, ponte --lg-*, vidro fosco, fallbacks
    ├── App.tsx                              ✏️ Layout com AppBackground + AppNavigation
    ├── lib/contrast.ts (+ test)             ➕ luminância WCAG e pares de tokens (R52)
    ├── hooks/useMediaQuery.ts (+ test)      ➕ 768px, reduzir movimento
    ├── components/
    │   ├── AppHeader.tsx                    ✏️ cabeçalho de vidro (≥768px com as abas)
    │   ├── AppNavigation.tsx (+ test)       ➕ TabBar, rota marcada, "Sair" com confirmação, some com teclado
    │   ├── AppBackground.tsx                ➕ degradê festivo com data-glisse-backdrop
    │   ├── ui/
    │   │   ├── Button.tsx                   ✏️ sobre glisse Button (mesma API)
    │   │   ├── Input.tsx                    ✏️ <input> nativo com as classes lg-input da Glisse
    │   │   ├── Card.tsx                     ✏️ vidro fosco; highlight → glisse Glass
    │   │   ├── Modal.tsx, ConfirmModal.tsx  ✏️ glisse Glass, foco preso
    │   │   ├── Toast.tsx, Badge.tsx, Spinner.tsx ✏️
    │   │   └── icons.tsx                    ➕
    │   └── group/*                          ✏️ ajustes de layout e de cartão de destaque (US2)
    ├── pages/*                              ✏️ ajustes de layout (US3)
    └── test/setup.ts                        ✏️ stubs para a Glisse no jsdom (R53)
```

**Structure Decision**: a Glisse fica atrás de `components/ui/*` (mesma API de hoje), então o
redesenho acontece em camadas: tokens e componentes base primeiro (todas as telas mudam juntas),
depois os ajustes de cada tela. Nenhuma página importa a Glisse direto.

## Riscos e mitigação

| Risco | Mitigação |
|-------|-----------|
| CSP bloqueia o `<style>` injetado e o visual some em produção | CSS empacotado + marcador `glisse-styles` (R44); passo 1 do quickstart confere sem violação no console |
| Biblioteca nova, pré-1.0, de um autor, muda ou some | versão exata; isolada em `ui/*` (trocar a base não toca as telas); varreduras no CI |
| Vidro pesado no celular | lentes só em TabBar, janelas e 2 cartões; resto vidro fosco; medida do SC-004 no quickstart |
| Contraste ruim sobre o vidro | pior caso calculado nos tokens (data-model) e testado (R52); texto nunca direto no fundo |
| Erro confundido com o vermelho da marca | erro sempre com ícone + texto; cor de erro distinta (`#be123c`/`#fb7185`) |
| Testes quebram no jsdom (canvas, ResizeObserver) | stubs no `setup.ts` (R53); consultas por papel/nome não dependem da implementação |
| Barra de abas cobre conteúdo ou o teclado | espaço reservado com área segura; barra some com campo em foco (R49) |
| react-hook-form deixa de funcionar com o `Input` da Glisse (controlado) | o `Input` do projeto usa `<input>` nativo com as classes da Glisse (R46); testes de valores iniciais e `reset()` (T011) |

## Complexity Tracking

Não se aplica: o Constitution Check não teve violações.
