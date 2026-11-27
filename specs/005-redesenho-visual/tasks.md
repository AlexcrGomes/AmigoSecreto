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

- [ ] T010 [P] [US1] Ampliar `frontend/src/components/ui/Button.test.tsx`: cada `variant` renderiza um botão acessível com o rótulo; `primary` usa o preenchimento principal e `danger` a marca destrutiva da Glisse; `loading` mostra o `Spinner`, `aria-busy` e desabilita; `fullWidth`; `type="button"` por padrão; clique chama `onClick`; `buttonClasses` continua existindo para links
- [ ] T011 [P] [US1] Ampliar `frontend/src/components/ui/Input.test.tsx`: rótulo visível ligado ao campo; erro com ícone, texto, `aria-invalid` e `aria-describedby`; com `register()` do react-hook-form, em `text`, `email`, `password`, `date`, `time` e `number`: o valor digitado chega ao `handleSubmit`, o valor de `defaultValues` aparece no campo ao montar e um `reset({ … })` depois da montagem atualiza o campo; o elemento é um `<input>` nativo com as classes `lg-glass lg-input` (research R46)
- [ ] T012 [P] [US1] Ampliar `frontend/src/components/ui/Modal.test.tsx`: `role="dialog"` com `aria-modal` e título; foco vai para dentro ao abrir e fica preso com Tab/Shift+Tab; `Esc` fecha; o foco volta ao elemento que abriu
- [ ] T013 [P] [US1] Ampliar `frontend/src/components/ui/Toast.test.tsx` e criar `frontend/src/components/ui/Card.test.tsx` e `frontend/src/components/AppBackground.test.tsx`: aviso de erro com ícone e texto (`role="status"`); `Card` comum com a classe de vidro fosco e `Card highlight` com a superfície de vidro da Glisse; o fundo tem `data-glisse-backdrop` e `aria-hidden`

### Implementation for User Story 1

- [ ] T014 [P] [US1] Criar `frontend/src/components/ui/icons.tsx` (`IconGroups`, `IconPlus`, `IconTicket`, `IconLogout`, `IconAlert`, `IconCheck`; SVG com `aria-hidden` e `currentColor` — research R51)
- [ ] T015 [US1] Reescrever `frontend/src/components/ui/Button.tsx` sobre o `Button` da Glisse (`primary`→`filled`; `secondary`/`ghost`→`glass`; `danger`→`glass` + `destructive`), mantendo `loading`, `fullWidth`, `min-h-11` e a mesma API; `buttonClasses` com os tokens no mesmo formato de pílula — faz T010 passar
- [ ] T016 [US1] Reescrever `frontend/src/components/ui/Input.tsx`: `<input>` nativo (não o `Input` da Glisse, que é controlado — research R46) com as classes `lg-glass lg-input` da Glisse e os tokens, em todos os tipos, mantendo a API e a compatibilidade com `register()`; rótulo visível, erro com `IconAlert` — faz T011 passar
- [ ] T017 [US1] Reescrever `frontend/src/components/ui/Modal.tsx` e `frontend/src/components/ui/ConfirmModal.tsx` com a superfície `Glass` da Glisse sobre um véu, mantendo foco preso, `Esc` e devolução do foco — faz T012 passar
- [ ] T018 [P] [US1] Reescrever `frontend/src/components/ui/Card.tsx` (vidro fosco; prop `highlight` → `Glass` da Glisse), `Toast.tsx` (vidro, ícones de sucesso/erro), `Badge.tsx` (pílula, tons dos tokens) e `Spinner.tsx` (sem animação com reduzir movimento) — faz T013 passar (parte dos componentes)
- [ ] T019 [US1] Criar `frontend/src/components/AppBackground.tsx` (degradê e manchas de luz de [data-model.md](./data-model.md) §2, fixo, `data-glisse-backdrop`, `aria-hidden`) e usá-lo no `Layout` de `frontend/src/App.tsx`; `AppHeader.tsx` vira um cabeçalho de vidro (por enquanto com o "Sair" atual; a navegação nova vem na US3) — faz T013 passar
- [ ] T020 [US1] Rodar a suíte inteira do frontend e corrigir o que quebrou pela troca dos componentes **sem mudar textos, rotas ou consultas por papel/nome** dos testes existentes; se algum teste dependia de classe CSS antiga, trocar pela consulta acessível equivalente

**Checkpoint**: todas as telas já aparecem no visual novo, nos dois temas, com os fallbacks de transparência e movimento.

---

## Phase 4: User Story 2 - A tela do grupo redesenhada (Priority: P1)

**Goal**: cada parte da tela do grupo no visual novo, com cartões de destaque e a revelação marcante.

**Independent Test**: percorrer a tela do grupo aguardando sorteio, sorteada e finalizada, como organizador, participante e responsável, a 360px; tudo no visual novo, sem rolagem horizontal, todas as ações funcionando.

### Tests for User Story 2 ⚠️

- [ ] T021 [P] [US2] Ampliar `frontend/src/components/group/SecretFriendCard.test.tsx` e `frontend/src/components/group/DependentSecretFriendCard.test.tsx`: cartão de destaque (superfície de vidro da Glisse); oculto, o nome **não está no DOM** (`document.body.textContent` não contém o nome) e só os pontos aparecem; revelado, o nome aparece; com `prefers-reduced-motion`, sem classe de transição (FR-014, research R50)
- [ ] T022 [P] [US2] Ampliar `frontend/src/pages/GroupPage.open.test.tsx`, `GroupPage.drawn.test.tsx` e `GroupPage.finished.test.tsx`: selo de status em pílula com o texto ("Aguardando sorteio", "Sorteado", "Finalizado"); as seções continuam na mesma ordem; avisos (sorteio feito, desfeito, finalizado) com ícone e texto; `ConfirmParticipationCard` como cartão de destaque

### Implementation for User Story 2

- [ ] T023 [US2] Alterar `frontend/src/components/group/SecretFriendCard.tsx` e `DependentSecretFriendCard.tsx`: `Card highlight`, nome em destaque com transição de opacidade/escala ≤ 300 ms e `motion-reduce:transition-none` — faz T021 passar
- [ ] T024 [US2] Alterar `frontend/src/pages/GroupPage.tsx`: cabeçalho do grupo com nome e `Badge` de status; avisos como faixas de vidro com ícone (`IconCheck`/`IconAlert`); espaçamento entre cartões pelos tokens — faz T022 passar
- [ ] T025 [P] [US2] Ajustar o layout no visual novo de `frontend/src/components/group/EventDetailsCard.tsx`, `ShareCode.tsx`, `DrawButton.tsx` e `ConfirmParticipationCard.tsx` (este como `Card highlight`), sem mudar textos nem ações
- [ ] T026 [P] [US2] Ajustar o layout no visual novo de `frontend/src/components/group/WishlistEditor.tsx`, `WishlistView.tsx`, `ExternalLinkModal.tsx`, `EventDetailsForm.tsx` (campos de data/hora/valor com `.lg-input`)
- [ ] T027 [P] [US2] Ajustar o layout no visual novo de `frontend/src/components/group/DependentsCard.tsx`, `DependentForm.tsx`, `ParticipantList.tsx` (linhas com selos em pílula, "(dependente de Ana)" em texto secundário), `ExitRequestsList.tsx` e `ExitRequestPanel.tsx`
- [ ] T028 [US2] Rodar a suíte do frontend; corrigir quebras mantendo as consultas por papel/nome

**Checkpoint**: US1 + US2 entregam o visual novo na tela mais usada.

---

## Phase 5: User Story 3 - Telas de conta e de grupos + navegação por abas (Priority: P2)

**Goal**: entrar, criar conta, "Meus grupos", criar grupo e entrar com código no visual novo; abas embaixo no celular e no topo a partir de 768px; confirmação ao sair.

**Independent Test**: criar conta, entrar, navegar pelas abas a 360px e a 1280px nos dois temas; "Sair" pede confirmação; o fim das telas fica acima da barra.

### Tests for User Story 3 ⚠️

- [ ] T029 [P] [US3] Criar `frontend/src/components/AppNavigation.test.tsx`: itens "Meus grupos", "Criar grupo", "Entrar com código" e "Sair"; a aba marcada segue a rota (`/` e `/grupos/:id` → "Meus grupos"; `/grupos/novo`; `/entrar-no-grupo`); tocar numa aba navega; "Sair" abre "Sair da sua conta?" — cancelar mantém a rota, não chama `POST /auth/logout` e a aba marcada continua a da rota (nunca "Sair"), confirmar chama e vai para `/entrar`; a < 768px a barra fica embaixo e some com um campo de texto em foco; a ≥ 768px fica no cabeçalho; só uma navegação no DOM (research R49)
- [ ] T030 [P] [US3] Ampliar `frontend/src/components/AppHeader.test.tsx`, `frontend/src/pages/LoginPage.test.tsx` e `RegisterPage.test.tsx`: sem sessão, cabeçalho só com o nome do app e sem navegação; formulário num cartão de vidro; erros com ícone e texto
- [ ] T031 [P] [US3] Ampliar `frontend/src/pages/HomePage.test.tsx`, `CreateGroupPage.test.tsx` e `JoinGroupPage.test.tsx`: grupos em cartões nas seções "Em andamento" e "Finalizados"; os botões "Criar grupo" e "Entrar com código" continuam; formulários no visual novo com as mesmas validações

### Implementation for User Story 3

- [ ] T032 [US3] Criar `frontend/src/components/AppNavigation.tsx`: `TabBar` da Glisse com adaptador `href → <Link to>` do React Router e os ícones de `icons.tsx`; `TabBar` **controlada**: `value` sempre calculado da rota, `onValueChange` só trata "Sair", que nunca fica marcada (research R49); "Sair" abre `ConfirmModal` e chama o `logout` atual; `useMediaQuery('(min-width: 768px)')` decide fixo embaixo ou dentro do `AppHeader`; esconde com `focusin`/`focusout` em campos de texto; `padding-bottom: env(safe-area-inset-bottom)` — faz T029 passar
- [ ] T033 [US3] Alterar `frontend/src/components/AppHeader.tsx` e o `Layout` de `frontend/src/App.tsx`: cabeçalho de vidro com o nome do app (e, ≥ 768px, a `AppNavigation` e o nome do usuário); `<main>` com espaço inferior `--tabbar-height` + área segura quando a barra está embaixo; sem navegação nas telas de visitante — faz T029 e T030 passarem
- [ ] T034 [P] [US3] Ajustar o layout no visual novo de `frontend/src/pages/LoginPage.tsx`, `RegisterPage.tsx` e `PagePlaceholder.tsx` — faz T030 passar
- [ ] T035 [P] [US3] Ajustar o layout no visual novo de `frontend/src/pages/HomePage.tsx` (cartões de grupo, aviso de saída aprovada), `CreateGroupPage.tsx` e `JoinGroupPage.tsx` — faz T031 passar
- [ ] T036 [US3] Rodar a suíte do frontend; corrigir quebras mantendo as consultas por papel/nome

**Checkpoint**: as três histórias juntas; todo o sistema no visual novo.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T037 [P] Atualizar `README.md` (visual novo, tema do aparelho, Glisse como dependência e por quê) e `CONTRIBUTING.md` (regra: Glisse só em `components/ui/*`; cores só por tokens)
- [ ] T038 Rodar `mvn -B verify` (backend, sem mudanças) e `npm run lint && npm run format:check && npx tsc -b && npm run test:coverage && npm run build` nos containers; corrigir até ficar verde com cobertura ≥ 80%
- [ ] T039 Subir com `docker compose up -d --build` e executar o roteiro do [quickstart.md](./quickstart.md) (§1 sem violação de CSP no console, §2 passos 1–16 nos dois temas a 360px e 1280px, §3 contraste, 360px, Lighthouse e sigilo); registrar o resultado no PR com capturas de tela
- [ ] T040 Revisão final contra a constituição v1.3.0 (Princípios I a VIII), conferindo em especial: CSP inalterada em `frontend/nginx.conf`; Glisse importada só onde as regras transversais permitem; `glisse` fixada em 0.6.0 sem `^`; nenhuma outra dependência nova; nenhum nome oculto no DOM

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup; **bloqueia todas as histórias**.
- **US1 (Phase 3)**: depende da Foundational.
- **US2 (Phase 4)**: depende da US1 (componentes base).
- **US3 (Phase 5)**: depende da US1; independente da US2 (exceto `App.tsx`, que a US1 também toca).
- **Polish (Phase 6)**: depende de todas as histórias.

```text
Setup ─▶ Foundational ─▶ US1 ─┬─▶ US2 ─┐
                              └─▶ US3 ─┴─▶ Polish
```

### Within Each User Story

- Testes primeiro, vendo-os **falhar**.
- Tokens → componentes base → telas.
- Commit ao fim de cada tarefa ou grupo lógico (Conventional Commits).

### Parallel Opportunities

- Foundational: T003 e T004 juntos; depois T007, T008 e T009 (T005 e T006 em sequência, mexem na base dos testes e no carregamento).
- US1: todos os testes [P] juntos; T014 e T018 em paralelo; T015, T016, T017 um de cada vez (podem compartilhar o `Spinner`/ícones).
- US2: T025, T026 e T027 em paralelo (arquivos diferentes).
- US3: T034 e T035 em paralelo; T032 → T033.

---

## Parallel Example: User Story 1

```bash
# Testes primeiro, em paralelo:
Task: "T010 Button.test.tsx"
Task: "T011 Input.test.tsx"
Task: "T012 Modal.test.tsx"
Task: "T013 Toast/Card/AppBackground tests"

# Depois, peças independentes em paralelo:
Task: "T014 icons.tsx"
Task: "T018 Card, Toast, Badge, Spinner"
```

---

## Implementation Strategy

### MVP

**MVP = Setup + Foundational + US1**: com os componentes base trocados, todas as telas já ficam
no visual novo, nos dois temas. US2 e US3 refinam cada tela e trazem a navegação por abas.

1. Setup → Foundational → Glisse carregando sem violar a CSP; contraste provado.
2. US1 → **parar e validar** (visual novo em todas as telas, dois temas, fallbacks).
3. US2 → **parar e validar** (tela do grupo, revelação sem o nome no DOM).
4. US3 → validar (navegação, sair com confirmação) → Polish → PR.

### Sugestão de entregas (PRs menores)

| PR | Conteúdo |
|----|----------|
| 1 | Phase 1 + Phase 2 + US1 |
| 2 | US2 |
| 3 | US3 + Polish (com o roteiro manual) |

Cada PR passa pelo CI, pelas varreduras e pela aprovação manual (`aprovacao-manual`).

---

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta.
- [USn] mapeia a tarefa para a história da [spec.md](./spec.md).
- Todo teste deve falhar antes da implementação correspondente.
- Evitar: importar a Glisse fora de `ui/`, cor solta fora dos tokens, mudar texto ou fluxo, e
  qualquer efeito que deixe um nome oculto no DOM.
