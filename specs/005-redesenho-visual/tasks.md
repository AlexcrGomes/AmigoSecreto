---

description: "Lista de tarefas da feature 005 — redesenho visual"
---

# Tasks: Redesenho Visual

**Input**: Design documents from `/specs/005-redesenho-visual/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **OBRIGATÓRIOS** (Constituição IV). Em cada história, as tarefas de teste vêm **antes**
da implementação e MUST falhar antes do código existir. Frontend: Vitest + React Testing Library +
MSW. Gate de cobertura: 80% de linhas. O backend não muda, mas `mvn -B verify` continua no CI.

**Organization**: tarefas agrupadas por história de usuário (US1–US3 da spec).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa incompleta)
- **[Story]**: história de usuário da tarefa (US1, US2, US3)

## Path Conventions

- Frontend: `frontend/src/…`; testes ao lado do código (`*.test.ts(x)`)
- Docker roda **no WSL**: testes no container `node:24.22.3-alpine3.24`, como nas features anteriores

## Regras transversais (valem para TODAS as tarefas)

- **A Glisse só é importada em `frontend/src/components/ui/*`, `AppNavigation.tsx`, `main.tsx` e
  `test/setup.ts`** (research R43, R46). O `Input` do projeto usa só as **classes** dela, não o
  componente. Páginas e `components/group/*` usam os componentes de `ui/`.
- **A API (props) dos componentes de `ui/` não muda** ([contracts/ui-components.md](./contracts/ui-components.md));
  os testes existentes, que consultam por papel e nome acessível, MUST continuar passando sem
  reescrever as consultas.
- **Textos, rotas e fluxos não mudam** (FR-013); a única exceção é a confirmação ao sair (FR-015).
- **Cores só pelos tokens** de [data-model.md](./data-model.md); nada de cor solta no código.
- **CSP intacta**: nenhuma mudança em `frontend/nginx.conf` (research R44).
- **Sigilo** (FR-014): com o nome oculto, o nome não existe no DOM; nada no armazenamento do navegador.
- Toque ≥ 44×44px, 360px sem rolagem horizontal, erro sempre com ícone + texto.
- **Sem dependências novas** além de `glisse` (R43).

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Confirmar a base: na branch `005-redesenho-visual`, rodar `npm run lint && npm run format:check && npx tsc -b && npm run test:coverage && npm run build` no container do Node e registrar que tudo está verde antes de mudar código
- [X] T002 Adicionar `glisse` **0.6.0 exata** (sem `^`) em `frontend/package.json` `dependencies` e atualizar `frontend/package-lock.json` (`npm install glisse@0.6.0 --save-exact` no container); conferir com `npm audit --omit=dev` que não há vulnerabilidade nova (research R43)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Glisse carregando sem violar a CSP, testes funcionando com ela no jsdom, tokens e contraste.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase terminar.

### Tests (escrever primeiro e ver falhar)

- [X] T003 [P] Criar `frontend/src/lib/contrast.test.ts`: a função de razão de contraste dá 21 para preto/branco e 1 para cores iguais; compor uma cor translúcida sobre um fundo; **todos os pares texto/fundo de [data-model.md](./data-model.md) §1**, nos dois temas, sobre o vidro composto no pior fundo do degradê (§2), têm razão ≥ 4,5 (texto) — incluindo `on-primary`/`primary-fill` (research R52)
- [X] T004 [P] Criar `frontend/src/hooks/useMediaQuery.test.tsx`: devolve o estado de `matchMedia` e atualiza quando o evento `change` dispara (usado para 768px e "reduzir movimento")

### Implementation

- [X] T005 Alterar `frontend/src/test/setup.ts`: stubs de `HTMLCanvasElement.prototype.getContext` (devolve `null`), `ResizeObserver`, `matchMedia` (claro, sem reduzir movimento, largura de celular por padrão) e `requestAnimationFrame`/`cancelAnimationFrame` se faltarem, para a Glisse rodar no jsdom sem erros no console (research R53); rodar a suíte inteira e confirmar que continua verde
- [X] T006 Alterar `frontend/index.html`: `<meta id="glisse-styles" name="glisse-styles" content="bundled">` no `<head>` (impede a injeção do `<style>` — research R44), `viewport-fit=cover` no viewport e `theme-color` com uma versão para cada tema (`media="(prefers-color-scheme: dark)"`); alterar `frontend/src/main.tsx` para `import 'glisse/styles.css'` **antes** de `./index.css`
- [X] T007 Criar `frontend/src/lib/contrast.ts` (luminância relativa WCAG 2.1, razão de contraste, composição alfa) e `frontend/src/lib/designTokens.ts` (os valores de [data-model.md](./data-model.md) §1–§2 por tema, fonte única lida pelo teste) — faz T003 passar
- [X] T008 Reescrever os tokens em `frontend/src/index.css`: `@theme` com as cores festivas de [data-model.md](./data-model.md) §1 (claro) e o bloco `prefers-color-scheme: dark` (escuro), `--radius-*`, `--blur-glass`, `--shadow-glass`, `--tabbar-height`; ponte `--lg-*` → tokens (§4); utilitário de superfície de vidro fosco (`.surface-glass`) e os estados de §5 (`prefers-reduced-transparency`, `@supports not (backdrop-filter…)`, `prefers-reduced-motion`); `body` sem fundo próprio (vem do `AppBackground`); valores iguais aos de `designTokens.ts`
- [X] T009 [P] Criar `frontend/src/hooks/useMediaQuery.ts` — faz T004 passar

**Checkpoint**: a Glisse carrega pelo CSS empacotado, os testes rodam com ela, os tokens novos estão no lugar e o contraste está provado.

---

## Phase 3: User Story 1 - Identidade visual nova, coerente e nos dois temas (Priority: P1) 🎯 MVP

**Goal**: componentes base, fundo festivo e tema do aparelho — como todas as telas usam os componentes base, todas mudam de cara nesta fase.

**Independent Test**: abrir qualquer tela em modo claro e escuro; visual novo e legível nos dois; trocar o modo do aparelho muda o tema na hora; com "reduzir transparência", superfícies sólidas.

### Tests for User Story 1 ⚠️

- [X] T010 [P] [US1] Ampliar `frontend/src/components/ui/Button.test.tsx`: cada `variant` renderiza um botão acessível com o rótulo; `primary` usa o preenchimento principal e `danger` a marca destrutiva da Glisse; `loading` mostra o `Spinner`, `aria-busy` e desabilita; `fullWidth`; `type="button"` por padrão; clique chama `onClick`; `buttonClasses` continua existindo para links
- [X] T011 [P] [US1] Ampliar `frontend/src/components/ui/Input.test.tsx`: rótulo visível ligado ao campo; erro com ícone, texto, `aria-invalid` e `aria-describedby`; com `register()` do react-hook-form, em `text`, `email`, `password`, `date`, `time` e `number`: o valor digitado chega ao `handleSubmit`, o valor de `defaultValues` aparece no campo ao montar e um `reset({ … })` depois da montagem atualiza o campo; o elemento é um `<input>` nativo com as classes `lg-glass lg-input` (research R46)
- [X] T012 [P] [US1] Ampliar `frontend/src/components/ui/Modal.test.tsx`: `role="dialog"` com `aria-modal` e título; foco vai para dentro ao abrir e fica preso com Tab/Shift+Tab; `Esc` fecha; o foco volta ao elemento que abriu
- [X] T013 [P] [US1] Ampliar `frontend/src/components/ui/Toast.test.tsx` e criar `frontend/src/components/ui/Card.test.tsx` e `frontend/src/components/AppBackground.test.tsx`: aviso de erro com ícone e texto (`role="status"`); `Card` comum com a classe de vidro fosco e `Card highlight` com a superfície de vidro da Glisse; o fundo tem `data-glisse-backdrop` e `aria-hidden`

### Implementation for User Story 1

- [X] T014 [P] [US1] Criar `frontend/src/components/ui/icons.tsx` (`IconGroups`, `IconPlus`, `IconTicket`, `IconLogout`, `IconAlert`, `IconCheck`; SVG com `aria-hidden` e `currentColor` — research R51)
- [X] T015 [US1] Reescrever `frontend/src/components/ui/Button.tsx` sobre o `Button` da Glisse (`primary`→`filled`; `secondary`/`ghost`→`glass`; `danger`→`glass` + `destructive`), mantendo `loading`, `fullWidth`, `min-h-11` e a mesma API; `buttonClasses` com os tokens no mesmo formato de pílula — faz T010 passar
- [X] T016 [US1] Reescrever `frontend/src/components/ui/Input.tsx`: `<input>` nativo (não o `Input` da Glisse, que é controlado — research R46) com as classes `lg-glass lg-input` da Glisse e os tokens, em todos os tipos, mantendo a API e a compatibilidade com `register()`; rótulo visível, erro com `IconAlert` — faz T011 passar
- [X] T017 [US1] Reescrever `frontend/src/components/ui/Modal.tsx` e `frontend/src/components/ui/ConfirmModal.tsx` com a superfície `Glass` da Glisse sobre um véu, mantendo foco preso, `Esc` e devolução do foco — faz T012 passar
- [X] T018 [P] [US1] Reescrever `frontend/src/components/ui/Card.tsx` (vidro fosco; prop `highlight` → `Glass` da Glisse), `Toast.tsx` (vidro, ícones de sucesso/erro), `Badge.tsx` (pílula, tons dos tokens) e `Spinner.tsx` (sem animação com reduzir movimento) — faz T013 passar (parte dos componentes)
- [X] T019 [US1] Criar `frontend/src/components/AppBackground.tsx` (degradê e manchas de luz de [data-model.md](./data-model.md) §2, fixo, `data-glisse-backdrop`, `aria-hidden`) e usá-lo no `Layout` de `frontend/src/App.tsx`; `AppHeader.tsx` vira um cabeçalho de vidro (por enquanto com o "Sair" atual; a navegação nova vem na US3) — faz T013 passar
- [X] T020 [US1] Rodar a suíte inteira do frontend e corrigir o que quebrou pela troca dos componentes **sem mudar textos, rotas ou consultas por papel/nome** dos testes existentes; se algum teste dependia de classe CSS antiga, trocar pela consulta acessível equivalente

**Checkpoint**: todas as telas já aparecem no visual novo, nos dois temas, com os fallbacks de transparência e movimento.

---

## Phase 4: User Story 2 - A tela do grupo redesenhada (Priority: P1)

**Goal**: cada parte da tela do grupo no visual novo, com cartões de destaque e a revelação marcante.

**Independent Test**: percorrer a tela do grupo aguardando sorteio, sorteada e finalizada, como organizador, participante e responsável, a 360px; tudo no visual novo, sem rolagem horizontal, todas as ações funcionando.

### Tests for User Story 2 ⚠️

- [X] T021 [P] [US2] Ampliar `frontend/src/components/group/SecretFriendCard.test.tsx` e `frontend/src/components/group/DependentSecretFriendCard.test.tsx`: cartão de destaque (superfície de vidro da Glisse); oculto, o nome **não está no DOM** (`document.body.textContent` não contém o nome) e só os pontos aparecem; revelado, o nome aparece; com `prefers-reduced-motion`, sem classe de transição (FR-014, research R50)
- [X] T022 [P] [US2] Ampliar `frontend/src/pages/GroupPage.open.test.tsx`, `GroupPage.drawn.test.tsx` e `GroupPage.finished.test.tsx`: selo de status em pílula com o texto ("Aguardando sorteio", "Sorteado", "Finalizado"); as seções continuam na mesma ordem; avisos (sorteio feito, desfeito, finalizado) com ícone e texto; `ConfirmParticipationCard` como cartão de destaque

### Implementation for User Story 2

- [X] T023 [US2] Alterar `frontend/src/components/group/SecretFriendCard.tsx` e `DependentSecretFriendCard.tsx`: `Card highlight`, nome em destaque com transição de opacidade/escala ≤ 300 ms e `motion-reduce:transition-none` — faz T021 passar
- [X] T024 [US2] Alterar `frontend/src/pages/GroupPage.tsx`: cabeçalho do grupo com nome e `Badge` de status; avisos como faixas de vidro com ícone (`IconCheck`/`IconAlert`); espaçamento entre cartões pelos tokens — faz T022 passar
- [X] T025 [P] [US2] Ajustar o layout no visual novo de `frontend/src/components/group/EventDetailsCard.tsx`, `ShareCode.tsx`, `DrawButton.tsx` e `ConfirmParticipationCard.tsx` (este como `Card highlight`), sem mudar textos nem ações
- [X] T026 [P] [US2] Ajustar o layout no visual novo de `frontend/src/components/group/WishlistEditor.tsx`, `WishlistView.tsx`, `ExternalLinkModal.tsx`, `EventDetailsForm.tsx` (campos de data/hora/valor com `.lg-input`)
- [X] T027 [P] [US2] Ajustar o layout no visual novo de `frontend/src/components/group/DependentsCard.tsx`, `DependentForm.tsx`, `ParticipantList.tsx` (linhas com selos em pílula, "(dependente de Ana)" em texto secundário), `ExitRequestsList.tsx` e `ExitRequestPanel.tsx`
- [X] T028 [US2] Rodar a suíte do frontend; corrigir quebras mantendo as consultas por papel/nome

**Checkpoint**: US1 + US2 entregam o visual novo na tela mais usada.

---

## Revisão de 2026-11-27 (depois do PR #24)

As tarefas T001–T028 foram entregues no PR #24. O usuário pediu outra paleta e outra disposição
(spec, "Revisão 2026-11-27"; research R55–R60). As antigas T029–T040 (navegação no topo, polimento
nos dois temas) foram **substituídas** pelas abaixo, na branch `005-layout-responsivo`.

---

## Phase 5: Revisão da paleta — branco, vermelho e dourado, tema único (US1)

**Goal**: o visual inteiro em branco e vermelho com detalhes dourados, sem verde, sempre claro.

**Independent Test**: abrir qualquer tela com o aparelho em modo claro e em modo escuro; as duas
iguais, brancas com vermelho e dourado, sem verde; contraste AA provado pelo teste.

### Tests ⚠️

- [ ] T029 [US1] Reescrever `frontend/src/lib/contrast.test.ts` para o tema único (R55, data-model §1): cada par (`text`, `textMuted`, `primary`, `accentGold`, `success`, `danger`) ≥ 4,5 sobre o vidro composto em cada parada de `BACKGROUND_STOPS` (`#ffffff`, `#fffaf3`, `#fdf3e3`, `#f7e7e9`, `#f5e2ba`) e sobre a superfície sólida; `onPrimary` sobre `primaryFill`; o `index.css` usa exatamente os valores de `designTokens.ts`, **não** tem `@media (prefers-color-scheme: dark)` e não tem nenhum token verde (`accent-green`); `index.html` tem `<html … data-theme="light">` e um só `theme-color` `#ffffff`
- [ ] T030 [P] [US1] Ampliar `frontend/src/components/ui/Toast.test.tsx` (Badge `success` e `Notice` `success` usam a classe dourada `text-success`/`ring-success`, nunca verde) e `frontend/src/components/AppBackground.test.tsx` (três manchas: duas douradas e uma vermelha, sem verde)

### Implementation

- [ ] T031 [US1] Reescrever `frontend/src/lib/designTokens.ts` (um tema, valores do data-model §1 e `BACKGROUND_STOPS` do §2) e `frontend/src/index.css` (tokens únicos, sem bloco escuro, `--color-gold-soft`, `--color-surface-muted: #fdfaf6`, `--color-border: #eee2d3`, `--lg-on: var(--color-accent-gold)`, `color-scheme: light`, fundo branco com manchas `app-spot-red` e duas `app-spot-gold`) — faz T029 passar
- [ ] T032 [US1] Alterar `frontend/index.html` (`<html lang="pt-BR" data-theme="light">`, um só `<meta name="theme-color" content="#ffffff">`) e `frontend/src/components/AppBackground.tsx` (manchas novas); trocar qualquer uso de verde restante por dourado (`grep -rn "green" frontend/src`) — faz T029 e T030 passarem

**Checkpoint**: todas as telas já aparecem em branco, vermelho e dourado.

---

## Phase 6: User Story 3 - Navegação e telas de conta nos dois desenhos (Priority: P2)

**Goal**: barra de abas embaixo no celular; menu lateral no tablet, iPad e computador; entrar e criar
conta em duas metades no desenho largo; "Meus grupos" em lista ou grade; confirmação ao sair.

**Independent Test**: a 360px e a 1280px, criar conta, entrar, navegar pelos três destinos, criar
grupo e entrar com código; "Sair" pede confirmação; o fim das telas fica acima da barra.

### Tests ⚠️

- [ ] T033 [P] [US3] Criar `frontend/src/components/AppNavigation.test.tsx` (R49, R58): celular — `TabBar` com "Meus grupos", "Criar grupo", "Entrar com código" e "Sair", aba marcada pela rota (`/` e `/grupos/:id` → "Meus grupos"), some com um campo de texto em foco; ≥ 768px (`setMediaMatcher`) — `navigation` "Menu principal" lateral com os três links, o atual com `aria-current="page"`, o nome do usuário e "Sair"; só uma navegação no DOM; nos dois: "Sair" abre "Sair da sua conta?", cancelar não chama `POST /auth/logout` e mantém a rota e a marcação, confirmar chama e vai para `/entrar`; trocar a largura com a tela aberta troca a forma sem sair da rota
- [ ] T034 [P] [US3] Criar `frontend/src/components/AuthLayout.test.tsx` e ampliar `frontend/src/pages/LoginPage.test.tsx`, `RegisterPage.test.tsx` e `frontend/src/components/AppHeader.test.tsx`: sem sessão, nenhuma navegação; a marca "Amigo Secreto" aparece; o formulário fica num cartão de vidro; ≥ 768px, o painel da marca e o formulário ficam lado a lado (`md:grid-cols-2`); erros com ícone e texto
- [ ] T035 [P] [US3] Ampliar `frontend/src/pages/HomePage.test.tsx`, `CreateGroupPage.test.tsx` e `JoinGroupPage.test.tsx`: grupos em cartões nas seções "Em andamento" e "Finalizados", numa lista que vira grade (`md:grid-cols-2`); "Criar grupo" e "Entrar com código" continuam; os formulários de criar grupo e entrar com código ficam num cartão de largura limitada (`max-w-xl`), com as mesmas validações

### Implementation

- [ ] T036 [US3] Criar `frontend/src/components/AppNavigation.tsx`: `useMediaQuery('(min-width: 768px)')`; celular — `TabBar` da Glisse **controlada** pela rota, com adaptador `href → <Link to>` e os ícones de `icons.tsx`, fixa embaixo com `env(safe-area-inset-bottom)`, escondida com `focusin`/`focusout` em campos de texto; largo — `<nav aria-label="Menu principal">` lateral de vidro (5rem até 1023px, 16rem a partir de 1024px) com a marca, os links (`aria-current`), o nome do usuário e "Sair"; "Sair" abre `ConfirmModal` e chama o `logout` atual — faz T033 passar
- [ ] T037 [US3] Alterar o `Layout` de `frontend/src/App.tsx` e `frontend/src/components/AppHeader.tsx`: logado no celular — cabeçalho compacto de vidro com a marca + `<main>` com espaço inferior `--tabbar-height` + área segura; logado ≥ 768px — grade `[menu lateral | conteúdo até 72rem]`, sem cabeçalho de topo; visitante — sem navegação (as telas de conta usam o `AuthLayout`) — faz T033 e T034 passarem
- [ ] T038 [P] [US3] Criar `frontend/src/components/AuthLayout.tsx` (celular: marca acima + cartão; ≥ 768px: duas metades, painel da marca em vermelho com filetes dourados e frase curta já existente no app, formulário à direita) e usá-lo em `frontend/src/pages/LoginPage.tsx` e `RegisterPage.tsx`; ajustar `PagePlaceholder.tsx` — faz T034 passar
- [ ] T039 [P] [US3] Reorganizar `frontend/src/pages/HomePage.tsx` (título + botões lado a lado no celular e à direita no largo; lista → grade `md:grid-cols-2 xl:grid-cols-3`; cartões de grupo com nome, status em pílula, data e participantes), `CreateGroupPage.tsx` e `JoinGroupPage.tsx` (cartão `max-w-xl` centralizado no largo) — faz T035 passar
- [ ] T040 [US3] Rodar a suíte do frontend; corrigir quebras mantendo textos, rotas e consultas por papel/nome

**Checkpoint**: moldura e telas de conta nos dois desenhos.

---

## Phase 7: User Story 4 - A tela do grupo em abas no celular e em duas colunas no largo (Priority: P1)

**Goal**: a tela do grupo deixa de ser uma coluna longa: abas Resumo · Pessoas · Desejos no
celular; duas colunas no tablet, iPad e computador (R57).

**Independent Test**: a tela do grupo nos três status, como organizador, participante e
responsável, a 360px (abas) e a 1280px (duas colunas); girar o tablet com um item de lista sendo
editado não perde a edição.

### Tests ⚠️

- [ ] T041 [US4] Nos `frontend/src/pages/GroupPage.*.test.tsx` existentes, adicionar `beforeEach(() => setMediaMatcher((q) => q.includes('min-width: 768px')))` — rodam no desenho largo, **sem mudar nenhuma consulta** (R59); devem continuar passando
- [ ] T042 [P] [US4] Criar `frontend/src/components/ui/SegmentedTabs.test.tsx`: `radiogroup` com o rótulo; cada item é um `radio` com o nome; o atual com `aria-checked="true"`; tocar chama `onValueChange`
- [ ] T043 [P] [US4] Criar `frontend/src/pages/GroupPage.layout.test.tsx`: no celular — `radiogroup` "Partes do grupo" com "Resumo", "Pessoas" e "Desejos", "Resumo" marcada; as seções "Resumo", "Pessoas" e "Desejos" (regiões com título) estão no DOM e só a da aba aberta não tem `hidden`; tocar "Pessoas" mostra os participantes, esconde o resumo e põe `?aba=pessoas` no endereço; abrir `/grupos/g-1?aba=desejos` abre "Desejos"; `?aba=xyz` abre "Resumo"; com o amigo secreto revelado, trocar de aba e voltar mantém o nome (sem buscar de novo); no largo — sem `radiogroup`, nenhuma seção `hidden`, a coluna lateral contém Pessoas e Desejos; trocar a largura com um item de lista em edição mantém o campo e o texto digitado

### Implementation

- [ ] T044 [US4] Criar `frontend/src/components/ui/SegmentedTabs.tsx` sobre o `SegmentedControl` da Glisse (`aria-label` repassado; largura total no celular; itens com no mínimo 44px de altura via CSS fora de camada, como o `.lg-button.min-h-11`) — faz T042 passar
- [ ] T045 [US4] Reorganizar `frontend/src/pages/GroupPage.tsx` em três `<section aria-labelledby>` ("Resumo", "Pessoas", "Desejos", títulos visíveis só no largo e acessíveis nos dois), com o conteúdo do contrato [ui-routes.md](./contracts/ui-routes.md) na mesma ordem interna; celular — `SegmentedTabs` abaixo do cabeçalho do grupo, aba por `useSearchParams` (`replace`), `hidden` nas seções fora da aba; largo — grade `md:grid-cols-[minmax(0,1fr)_18rem] lg:grid-cols-[minmax(0,1fr)_22rem]`, Resumo à esquerda e Pessoas + Desejos à direita; cabeçalho do grupo ocupando as duas colunas; nenhuma seção desmonta ao trocar de aba ou de desenho — faz T041 e T043 passarem
- [ ] T046 [US4] Rodar a suíte do frontend; corrigir quebras mantendo textos e consultas

**Checkpoint**: as quatro histórias juntas; todo o sistema nos dois desenhos.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T047 [P] Atualizar `README.md` (visual novo branco, vermelho e dourado, tema único, dois desenhos, Glisse como dependência e por quê) e `CONTRIBUTING.md` (Glisse só em `components/ui/*`; cores só por tokens; troca de desenho só em 768px)
- [ ] T048 Rodar `mvn -B verify` (backend, sem mudanças) e `npm run lint && npm run format:check && npx tsc -b && npm run test:coverage && npm run build` nos containers; corrigir até ficar verde com cobertura ≥ 80%
- [ ] T049 Subir com `docker compose up -d --build` e executar o roteiro do [quickstart.md](./quickstart.md) (§1 sem violação de CSP no console, §2 nas larguras 360, 390, 768, 1024 e 1280px, §3 contraste, sem rolagem horizontal, Lighthouse e sigilo), com capturas de tela de celular e de computador no PR
- [ ] T050 Revisão final contra a constituição v1.3.0 (Princípios I a VIII), conferindo em especial: CSP inalterada em `frontend/nginx.conf`; Glisse importada só em `components/ui/*`; `glisse` fixada em 0.6.0 sem `^`; nenhuma outra dependência nova; nenhum nome oculto no DOM; nenhum verde e nenhum tema escuro

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phases 1–4 (T001–T028)**: entregues no PR #24.
- **Paleta (Phase 5)**: sem dependências; vem primeiro porque mexe nos tokens que tudo usa.
- **US3 (Phase 6)**: depende da Phase 5 (tokens), por causa das cores do menu e da marca.
- **US4 (Phase 7)**: depende da Phase 5; independente da US3, exceto o espaço do menu lateral no
  `Layout` (T037), que muda a largura disponível — fazer depois da US3.
- **Polish (Phase 8)**: depende de tudo.

```text
PR #24 (T001–T028) ─▶ Paleta ─▶ US3 ─▶ US4 ─▶ Polish
```

### Within Each Phase

- Testes primeiro, vendo-os **falhar**.
- Tokens → componentes base → moldura → telas.
- Commit ao fim de cada fase (Conventional Commits), com aprovação do usuário.

### Parallel Opportunities

- Paleta: T029 e T030 juntos.
- US3: T033, T034 e T035 juntos; T038 e T039 em paralelo; T036 → T037.
- US4: T042 e T043 juntos (T041 antes, mexe nos testes existentes); T044 → T045.

---

## Implementation Strategy

1. Paleta → **parar e validar** (branco, vermelho e dourado em todas as telas; sem verde).
2. US3 → **parar e validar** (barra embaixo no celular, menu lateral no largo, telas de conta).
3. US4 → **parar e validar** (abas no celular, duas colunas no largo, nada se perde ao girar).
4. Polish → roteiro manual nas cinco larguras → PR.

### Entrega

| PR | Conteúdo |
|----|----------|
| #24 (mesclado) | Phases 1–4 (base, US1, US2) |
| 2 | Revisão: paleta + US3 + US4 + Polish, na branch `005-layout-responsivo` (R60) |

Cada PR passa pelo CI, pelas varreduras e pela aprovação manual (`aprovacao-manual`).

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- [USn] mapeia a tarefa para a história da [spec.md](./spec.md).
- Todo teste deve falhar antes da implementação correspondente.
- Evitar: importar a Glisse fora de `ui/`, cor solta fora dos tokens, verde, tema escuro, mudar
  texto ou fluxo, desmontar seções da tela do grupo ao trocar de aba, e qualquer efeito que deixe
  um nome oculto no DOM.
