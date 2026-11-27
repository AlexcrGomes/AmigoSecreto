# Research: Redesenho Visual

Decisões técnicas da feature 005. A numeração continua a das features anteriores
(001: R1–R12; 002: R13–R25; 003: R26–R32; 004: R33–R42). Só o frontend muda; backend, banco,
contratos de API e eventos SSE ficam como estão.

O pacote `glisse@0.6.0` foi baixado e lido (tipos, JavaScript e CSS publicados) antes destas
decisões; os comportamentos citados abaixo vêm desse código, não só da documentação.

---

## R43. Adotar a Glisse como dependência

**Decision**: adicionar `glisse` **0.6.0, fixado na versão exata** em `dependencies` do frontend.

- Licença MIT; sem `dependencies` próprias; `peerDependencies` só `react`/`react-dom >= 18` (o
  projeto usa React 19.3). ~130 KB de JavaScript e 27 KB de CSS sem compactar.
- Componentes exportados: `Button`, `Glass`, `Input`, `SearchField`, `Menu`, `SegmentedControl`,
  `Slider`, `Switch`, `TabBar`.
- Risco aceito pelo usuário (spec, Assumptions): pacote de um mês (publicado em 24/10/2026), anterior
  à 1.0, de um único autor. Mitigação: versão exata (sem `^`), uso concentrado em poucos
  componentes do projeto (`components/ui/*`), que isolam a Glisse do resto do código; `npm audit` e
  Trivy continuam no CI.
- Política de dependências do projeto: a adoção é a necessidade da feature (pedido explícito);
  atualizações futuras só por segurança ou necessidade.

**Rationale**: é a escolha do usuário (spec, Decisões do pedido); o pacote é pequeno e não traz
dependências transitivas.

**Alternatives considered**: recriar o visual só com Tailwind — descartado pelo usuário no
clarify da spec.

---

## R44. Carregar o CSS da Glisse sem afrouxar a CSP

**Problema**: a Glisse injeta o próprio CSS em tempo de execução, criando um `<style
id="glisse-styles">` no `<head>` — e a CSP do nginx é `style-src 'self'`, que bloqueia `<style>`
inline. Os estilos sumiriam em produção (e no `docker compose`), mas não nos testes.

**Decision**: importar o arquivo publicado pelo pacote (`import 'glisse/styles.css'`) **antes** do
`index.css` do projeto, para o Vite empacotá-lo no CSS do site (servido por `'self'`), e pôr no
`index.html` um marcador `<meta id="glisse-styles" …>`. A Glisse só injeta o `<style>` quando
`document.getElementById("glisse-styles")` não existe; com o marcador, ela não injeta nada e a CSP
não registra violação.

- Os estilos que a Glisse aplica por JavaScript (`element.style.setProperty(…)`, 19 usos) passam
  pela API do DOM e **não** são bloqueados por `style-src`.
- As imagens de borda e de lente são desenhadas em `<canvas>` e usadas como `data:` — já permitido
  por `img-src 'self' data:`.
- Nenhuma mudança na CSP.

**Rationale**: mantém a CSP do Princípio III intacta e entrega os estilos pelo mesmo caminho do
resto do CSS.

**Alternatives considered**:
- `style-src 'unsafe-inline'` — afrouxaria a CSP do sistema inteiro por causa de uma biblioteca.
- Nonce/hash para o `<style>` — o conteúdo vem do pacote e muda a cada versão; nonce exigiria
  renderização no servidor.

---

## R45. Tokens do design e a ponte com a Glisse

**Decision**: um único conjunto de tokens em `frontend/src/index.css` (Tailwind 4 `@theme`), com
versão clara e escura, e as variáveis `--lg-*` da Glisse **apontando para esses tokens**:

- A Glisse define cores por variáveis CSS (`--lg-foreground`, `--lg-muted`, `--lg-glass`,
  `--lg-fill`, `--lg-destructive`, `--lg-on`, `--lg-ring`, `--lg-separator`…) em
  `:where(:root)` e troca para o escuro com `@media (prefers-color-scheme: dark)` — o mesmo
  mecanismo que o projeto já usa. Como as regras dela usam `:where()` (especificidade zero), o
  `index.css` do projeto as sobrescreve sem `!important`.
- Ex.: `--lg-fill: var(--color-primary)` (botão principal vermelho), `--lg-on: var(--color-success)`
  (switch ligado verde), `--lg-destructive: var(--color-danger)`.
- Paleta festiva (FR-016) e valores em [data-model.md](./data-model.md); cada par texto/fundo usado
  tem contraste AA calculado e testado (R52).

**Rationale**: Constituição VI ("conjunto consistente de cores… com Tailwind"); um lugar só para
trocar a identidade; a Glisse e os componentes do projeto saem da mesma paleta.

---

## R46. O que vem da Glisse e o que o projeto desenha

**Decision** (detalhes em [contracts/ui-components.md](./contracts/ui-components.md)):

| Componente do projeto | Base |
|-----------------------|------|
| `Button` (primary, secondary, ghost, danger) | `glisse` `Button` (`filled` / `glass` / `destructive`), embrulhado para manter `loading` (spinner + `aria-busy`), `fullWidth` e 44px |
| `buttonClasses` (links com cara de botão) | classes do projeto com os mesmos tokens e o mesmo formato do `Button` da Glisse |
| `Input` (todos os tipos) | `<input>` **nativo** com as classes `lg-glass lg-input` da própria Glisse (mesma aparência), compatível com o `register()` do react-hook-form; rótulo visível e erro abaixo |
| Navegação (abas) | `glisse` `TabBar` |
| `Modal` / `ConfirmModal` | superfície `glisse` `Glass` + foco preso e `Esc` do projeto |
| `Card` | vidro fosco do projeto (R47); `glisse` `Glass` só nos cartões de destaque (amigo secreto e confirmação) |
| `Toast`, `Badge`, `Spinner` | do projeto, nos tokens do vidro |

- O resto do código (páginas e `components/group/*`) continua usando só os componentes de
  `components/ui/*` — nada importa a Glisse direto, para isolar a dependência (R43).

**Rationale**: atende FR-002 e mantém a API atual dos componentes, então as telas mudam pouco e os
testes de comportamento continuam válidos.

**Por que o `Input` não usa o componente da Glisse** (achado do `/speckit-analyze`, lendo o código
publicado): o `Input` da Glisse é sempre **controlado** — guarda o valor em estado próprio
(`useControllable`), escreve `value` no `<input>` a cada renderização e espalha `...props` **depois**
do próprio `onChange`. Com o `register()` do react-hook-form (que escreve o valor inicial direto no
elemento e passa um `onChange`), o valor inicial seria apagado na próxima renderização e um
`onChange` nosso substituiria o dela, travando o campo. Usar as classes dela num `<input>` nativo
mantém a aparência (pílula de vidro, cores dos tokens) e só perde a distorção do fundo nos campos,
que ficam em vidro fosco — o mesmo nível dos cartões comuns (R47).

**Alternatives considered**:
- `Input` da Glisse com `Controller` do react-hook-form — reescreveria todos os formulários (login,
  cadastro, grupo, evento, lista, dependente) sem ganho visível e com risco nos testes existentes.
- `Input` da Glisse só com `defaultValue`/`onValueChange` adaptados — o valor inicial vindo de
  `reset()`/`defaultValues` depois da montagem não chega ao estado interno dela.

---

## R47. Vidro completo, vidro fosco e sólido (FR-008, FR-009)

**Decision**: três níveis, decididos por CSS e pela própria Glisse:

1. **Vidro completo** (distorção e brilho na borda): a Glisse só refrata o fundo "ao vivo" no
   Chromium; no WebKit (Safari/iPhone) ela refrata o que estiver marcado com
   `data-glisse-backdrop`. O fundo festivo do app fica dentro de um elemento com
   `data-glisse-backdrop`, então TabBar, botões e superfícies `Glass` dobram o fundo também no
   iPhone.
2. **Vidro fosco** (desfocado, sem distorção): os cartões comuns usam `backdrop-filter: blur(…)
   saturate(…)` com fundo translúcido dos tokens — barato e suportado em Safari, Chrome e Firefox.
3. **Sólido**: `@media (prefers-reduced-transparency: reduce)` e `@supports not (backdrop-filter:
   blur(1px))` trocam o fundo translúcido por um opaco (`--color-surface`) e zeram o desfoque; a
   Glisse já respeita `prefers-reduced-motion` nas animações dela, e o projeto desliga as próprias
   transições (`motion-reduce:`).

- **Desempenho (SC-004)**: cada `Glass` da Glisse desenha uma lente em `<canvas>`/WebGL; dezenas
  delas numa tela comprida pesariam no celular. Por isso só a TabBar, as janelas e os 2 cartões de
  destaque usam `Glass`; os demais cartões são vidro fosco (nível 2).

**Rationale**: o clarify definiu vidro fosco como alternativa (não sólido) e sólido só para
acessibilidade/navegador sem desfoque.

---

## R48. Fundo festivo (FR-005)

**Decision**: um componente `AppBackground` atrás de todas as telas, fixo, com `data-glisse-backdrop`:
degradê suave nos tons da paleta (vermelho → vinho/verde escuro no escuro; creme → rosado/dourado
claro no claro) e 3 a 4 manchas de luz desfocadas (gradientes radiais em vermelho, verde e
dourado). Só CSS — sem imagens nem ilustrações.

- O texto nunca fica direto sobre o fundo: sempre sobre uma superfície (vidro ou sólido), cujo
  contraste é o que se mede (R52).
- Com "reduzir transparência", o fundo continua (é só cor); as superfícies é que ficam sólidas.

---

## R49. Navegação: abas embaixo no celular, no topo a partir de 768px (FR-015)

**Decision**:

- Um `AppNavigation` com os itens "Meus grupos" (`/`), "Criar grupo" (`/grupos/novo`), "Entrar com
  código" (`/entrar-no-grupo`) e "Sair" (sem rota), usando o `TabBar` da Glisse com links do React
  Router (prop `link` com um adaptador `href → to`). O item marcado vem da rota atual; `/grupos/:id`
  marca "Meus grupos".
- **< 768px**: barra fixa embaixo, com `padding-bottom: env(safe-area-inset-bottom)`; o `<main>`
  ganha espaço inferior igual à altura da barra, para o fim de cada tela ficar acima dela.
  `index.html` passa a usar `viewport-fit=cover`.
- **≥ 768px**: a mesma `TabBar` dentro do cabeçalho de vidro, no topo, ao lado do nome do app e do
  usuário. A troca acontece por um hook de `matchMedia('(min-width: 768px)')` — um só `TabBar` no
  DOM, sem duplicar a navegação para leitores de tela.
- **Teclado aberto**: no celular, quando um campo de texto está em foco, a barra se esconde
  (`focusin`/`focusout`), para não cobrir o campo nem o botão de enviar.
- **"Sair"**: não navega; abre um `ConfirmModal` "Sair da sua conta?"; confirmar chama o `logout`
  atual e vai para `/entrar`; cancelar mantém a tela.
- **Aba marcada só pela rota**: a `TabBar` da Glisse marca a aba tocada por conta própria; por isso
  ela é usada **controlada** (`value` sempre calculado da rota e `onValueChange` só para "Sair"), e
  "Sair" nunca fica marcada — cancelar a confirmação mantém marcada a aba da tela atual.
- Telas de entrar e de criar conta: sem navegação (só o nome do app no topo).

---

## R50. A revelação do amigo secreto (US2, FR-014)

**Decision**: o cartão "Seu amigo secreto" e os cartões dos dependentes usam a superfície `Glass` de
destaque; ao revelar, o nome entra com uma transição de opacidade e escala (≤ 300 ms, desligada com
`prefers-reduced-motion`). O estado oculto continua renderizando os pontos (`••••••`) — **o nome
não existe no DOM** enquanto oculto, então nenhum efeito de vidro, desfoque ou transparência pode
deixá-lo legível. O desfoque é sempre do fundo, nunca de um nome.

---

## R51. Ícones

**Decision**: um conjunto pequeno de ícones SVG próprios em `components/ui/icons.tsx` (grupos,
criar, entrar com código, sair, alerta, sucesso), com `aria-hidden` e traço no estilo dos ícones do
iOS. Sem biblioteca de ícones nova.

- Erros sempre com ícone de alerta **e** texto (o vermelho de erro não pode ser o único sinal —
  spec, Edge Cases).

---

## R52. Contraste verificável sem dependência nova (FR-006, SC-002)

**Decision**: um módulo `lib/contrast.ts` com a fórmula de luminância relativa da WCAG 2.1 e um teste
(`contrast.test.ts`) que confere **todos os pares texto/fundo dos tokens**, nos dois temas, com
mínimo 4,5:1 (texto) e 3:1 (componentes e texto grande). Para o vidro, o fundo considerado é o
mais desfavorável: a cor translúcida da superfície composta sobre a cor mais clara (tema claro) ou
mais escura (tema escuro) do degradê.

- Complementa a conferência manual com as ferramentas do navegador no roteiro
  ([quickstart.md](./quickstart.md)).

**Alternatives considered**: `axe-core`/`vitest-axe` — dependência nova só para isso; o jsdom não
calcula cores compostas com `backdrop-filter`, então não pegaria o caso do vidro de qualquer jeito.

---

## R53. Testes com a Glisse no jsdom

**Decision**: `src/test/setup.ts` ganha stubs para o que o jsdom não tem e a Glisse usa:
`HTMLCanvasElement.prototype.getContext` (devolve `null`, e a Glisse cai no modo sem lente),
`ResizeObserver`, `matchMedia` (claro, sem reduzir movimento) e `requestAnimationFrame` se faltar.
Os testes continuam pelo papel e nome acessível (`getByRole('button', { name })`), então a troca do
componente por baixo não muda as consultas.

- Testes novos: navegação (abas, rota marcada, confirmação ao sair, abas escondidas com o teclado),
  `Button`/`Input` embrulhando a Glisse (loading, erro, `register`), `Modal` com foco preso,
  contraste (R52) e a revelação sem o nome no DOM.
- Gate de cobertura de 80% mantido.

---

## R54. Estratégia de entrega

**Decision**: três PRs, como nas features anteriores:

| PR | Conteúdo |
|----|----------|
| 1 | Fundação (US1): Glisse, CSP (R44), tokens e ponte `--lg-*` (R45), fundo (R48), componentes base (R46/R47), ícones, contraste — todas as telas já mudam de cara, porque usam os componentes base |
| 2 | Tela do grupo (US2): cartões de destaque, revelação, ajustes de layout de cada parte |
| 3 | Telas de conta e grupos + navegação por abas (US3), roteiro manual nos dois temas e a 360px |
