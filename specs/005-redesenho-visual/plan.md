# Implementation Plan: Redesenho Visual

**Branch**: `005-redesenho-visual` (revisão: `005-layout-responsivo`) | **Date**: 2026-11-27 | **Spec**: [spec.md](./spec.md)

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

### Revisão de 2026-11-27 (depois do PR #24)

O usuário mesclou a primeira entrega (base, US1 e US2) e pediu outra paleta e outra disposição. O
plano passa a ser, para o que falta:

- **Paleta** (R55): branco e vermelho com detalhes dourados, sem verde; **um tema só, claro** — sai
  o bloco escuro do `index.css`, `<html data-theme="light">` impede a Glisse de escurecer, sucesso
  vira dourado.
- **Dois desenhos em 768px** (R56): celular com cabeçalho compacto e `TabBar` embaixo; tablet, iPad e
  computador com menu lateral (R58), grades e colunas.
- **Tela do grupo** (R57): três partes sempre montadas — Resumo, Pessoas, Desejos —; abas
  (`SegmentedControl` da Glisse) e `hidden` no celular, duas colunas no desenho largo; aba no
  endereço (`?aba=`).
- **Testes** (R59): os testes de comportamento da tela do grupo rodam no desenho largo; testes novos
  para o celular.
- **Entrega** (R60): branch `005-layout-responsivo`, um PR com tudo o que falta.

## Technical Context

**Language/Version**: TypeScript 6.0 (frontend) — sem mudança; backend intocado

**Primary Dependencies**: React 19.3, Vite 8.3, Tailwind 4.3, React Router, TanStack Query 5,
react-hook-form + zod 4 (sem mudança). **Nova: `glisse` 0.6.0** (versão exata; MIT; só
`react`/`react-dom` como peer) — justificada em R43, por pedido explícito do usuário

**Storage**: N/A (nenhuma mudança de banco)

**Testing**: Vitest + React Testing Library + MSW (stubs de canvas/`ResizeObserver`/`matchMedia`
para a Glisse no jsdom, R53); teste de contraste dos tokens sem dependência nova (R52); roteiro
manual a 360px, 768px, 1024px e 1280px ([quickstart.md](./quickstart.md))

**Target Platform**: navegadores móveis e desktop (Chrome/Edge, Safari/iOS, Firefox); containers
como hoje

**Project Type**: aplicação web — só o `frontend/` muda

**Performance Goals**: primeira tela utilizável ≤ 3 s em celular intermediário com 4G (SC-004);
rolagem sem engasgos com o vidro (lentes `Glass` limitadas a TabBar, janelas e 2 cartões de
destaque, R47); +~40 KB compactados de JS/CSS da Glisse

**Constraints**: CSP atual intacta (`style-src 'self'`, R44); contraste WCAG AA no tema único
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
| VI | Mobile-first e moderno | 360px, 44px, AA (tema único claro, R55), rótulos e erros, e um desenho próprio para o celular (R56), estados de ação (FR-006 a FR-012). **Tailwind**: o estilo do projeto continua em Tailwind com tokens únicos (R45); o CSS da Glisse é o CSS de uma biblioteca de componentes, não CSS ad hoc do projeto, e é ajustado só por variáveis — ver nota abaixo | ✅ | ✅ |
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
    ├── index.css                            ✏️ tokens (revisão: branco/vermelho/dourado, tema único), ponte --lg-*, vidro fosco, fallbacks
    ├── App.tsx                              ✏️ Layout com AppBackground + AppNavigation (revisão: menu lateral ≥ 768px)
    ├── lib/contrast.ts (+ test)             ➕ luminância WCAG e pares de tokens (R52)
    ├── hooks/useMediaQuery.ts (+ test)      ➕ 768px, reduzir movimento
    ├── components/
    │   ├── AppHeader.tsx                    ✏️ cabeçalho de vidro (≥768px com as abas)
    │   ├── AppNavigation.tsx (+ test)       ➕ TabBar no celular, menu lateral ≥ 768px, "Sair" com confirmação
    │   ├── AuthLayout.tsx (+ test)          ➕ revisão: marca + formulário (duas metades ≥ 768px)
    │   ├── AppBackground.tsx                ➕ degradê festivo com data-glisse-backdrop
    │   ├── ui/
    │   │   ├── Button.tsx                   ✏️ sobre glisse Button (mesma API)
    │   │   ├── Input.tsx                    ✏️ <input> nativo com as classes lg-input da Glisse
    │   │   ├── Card.tsx                     ✏️ vidro fosco; highlight → glisse Glass
    │   │   ├── Modal.tsx, ConfirmModal.tsx  ✏️ glisse Glass, foco preso
    │   │   ├── Toast.tsx, Badge.tsx, Spinner.tsx ✏️
    │   │   ├── Notice.tsx                   ➕ avisos com ícone (US2)
    │   │   ├── SegmentedTabs.tsx (+ test)   ➕ revisão: abas do grupo sobre o SegmentedControl
    │   │   └── icons.tsx                    ➕
    │   └── group/*                          ✏️ ajustes de layout e de cartão de destaque (US2)
    ├── pages/*                              ✏️ disposição por desenho (US3, US4); GroupPage em três partes (R57)
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
| Glisse escurece sozinha no aparelho em modo escuro | `<html data-theme="light">` (R55); teste confere que o `index.css` não tem bloco escuro |
| Trocar de aba ou girar o tablet perde edição ou o nome revelado | partes sempre montadas, só `hidden` no celular (R57); teste de troca de desenho sem perder estado |
| Dourado com pouco contraste | dourado de texto `#a16207` (4,69:1 no pior caso); dourado claro só em decoração (data-model §1) |
| react-hook-form deixa de funcionar com o `Input` da Glisse (controlado) | o `Input` do projeto usa `<input>` nativo com as classes da Glisse (R46); testes de valores iniciais e `reset()` (T011) |

## Complexity Tracking

Não se aplica: o Constitution Check não teve violações.
