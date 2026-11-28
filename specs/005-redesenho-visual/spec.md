# Feature Specification: Redesenho Visual

**Feature Branch**: `005-redesenho-visual`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Redesenho visual de todo o sistema. A interface atual está feia; o usuário quer redefinir o design de todas as telas usando a biblioteca de componentes Glisse (https://glisse.kelvinzhang.ca/, pacote npm "glisse", estilo "Liquid Glass" do iOS 26). Decisões já tomadas com o usuário: (1) instalar a Glisse e usar os componentes dela onde existem (botões, switch, seletor segmentado, barra de abas, menu, busca e a superfície Glass para cartões e modais); o que a Glisse não tem (campos de texto/e-mail/senha/data/número, avisos tipo toast, selos, cartões, modais) é redesenhado pelo projeto no mesmo visual Liquid Glass, para o sistema ficar coerente; (2) o tema segue a configuração do aparelho (claro ou escuro). Nenhuma regra de negócio muda: só a aparência e a experiência das telas existentes (entrar, cadastrar, meus grupos, criar grupo, entrar no grupo, tela do grupo com detalhes do evento, confirmação, sorteio, amigo secreto, lista de desejos, dependentes, pedidos de saída e avisos). Continuam valendo mobile-first a partir de 360px, áreas de toque de 44px, contraste WCAG AA, pt-BR, estados de carregamento/sucesso/erro e o sigilo do resultado."

**Depende de**: features [001](../001-amigo-secreto-sorteio/spec.md) a
[004](../004-participantes-dependentes/spec.md). Todas as telas, textos, regras e fluxos delas
continuam valendo; esta feature muda **só a aparência e a experiência visual**.

**Glossário**:

- **Visual de vidro** ("Liquid Glass"): estilo em que cartões, botões e janelas parecem placas
  de vidro translúcido sobre um fundo colorido, com brilho nas bordas e leve distorção do que
  está por trás — inspirado no visual do iOS 26.
- **Superfície**: qualquer área que agrupa conteúdo (cartão, janela de confirmação, aviso,
  cabeçalho).
- **Tema**: o conjunto claro ou escuro de cores, escolhido pelo aparelho do usuário.

## Clarifications

### Session 2026-11-27

- Q: A navegação continua no cabeçalho do topo ou vira uma barra de abas fixa embaixo? → A: Barra de
  abas fixa embaixo, como nos apps do iPhone ("Meus grupos", "Criar grupo", "Entrar com código",
  "Sair").
- Q: Qual o tom das cores da identidade? → A: Festivo de fim de ano (vermelho, verde e dourado).
- Q: Qual fundo as telas devem ter por trás do vidro? → A: Degradê suave nos tons festivos, com
  manchas de luz desfocadas, igual em todas as telas.
- Q: O que mostrar nos navegadores que não desenham o efeito completo de vidro? → A: Vidro fosco
  simplificado (desfocado e translúcido, sem distorção); superfície sólida só com "reduzir
  transparência" ou em navegador muito antigo.
- Q: No computador, a navegação também fica numa barra de abas embaixo? → A: Não: barra embaixo só no
  celular; no tablet e no computador, a navegação fica no topo.
- Q: Tocar em "Sair" encerra a sessão na hora ou pede confirmação? → A: Pede confirmação ("Sair da
  sua conta?") antes de sair.

### Revisão 2026-11-27 (depois da primeira entrega, PR #24)

O usuário viu a primeira entrega e pediu: layout responsivo de verdade para celular, a disposição
dos cartões reorganizada de um jeito fácil e agradável, um design para celular e outro para
tablet/iPad/computador, e trocar o verde e vermelho por **branco e vermelho com detalhes
dourados**.

- Q: Quais as cores? → A: Branco e vermelho, com detalhes dourados; sem verde. (Substitui o "festivo
  vermelho, verde e dourado" da sessão anterior.)
- Q: O que fazer quando o aparelho está no modo escuro? → A: O sistema fica sempre claro (branco com
  vermelho e dourado), em qualquer aparelho. (Substitui "o tema segue o aparelho".)
- Q: No celular, como fica a tela do grupo, hoje uma coluna longa? → A: Abas no topo do grupo —
  "Resumo", "Pessoas" e "Desejos" —, cada uma mostrando só a sua parte.
- Q: Em tablet, iPad e computador, qual a disposição? → A: Menu lateral fixo à esquerda e, na tela do
  grupo, conteúdo em duas colunas (principal: resumo; lateral: pessoas e desejos). (Substitui "no
  tablet e no computador, a navegação fica no topo".)

**Decisões do pedido** (tomadas com o usuário antes da spec; não são detalhes a decidir no
plano):

- A biblioteca de componentes **Glisse** é a base do visual: os componentes que ela oferece são
  usados como são; o que ela não oferece é desenhado pelo projeto no mesmo estilo, para nenhuma
  parte do sistema destoar.
- O sistema tem **um só tema, claro** (branco, vermelho e dourado), independente do aparelho
  (revisão de 2026-11-27).
- Há **dois desenhos**: um para celular (menos de 768px) e outro para tablet, iPad e computador
  (768px ou mais) — revisão de 2026-11-27.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Uma identidade visual nova e coerente: branco, vermelho e dourado (Priority: P1)

Quem abre o sistema vê um visual novo e bonito, igual em todas as telas: fundo branco com brilhos
suaves de vermelho e dourado, cartões e janelas de vidro claro, botões vermelhos, detalhes
dourados, campos, selos e avisos no mesmo estilo. O visual é sempre claro, qualquer que seja o modo
do aparelho.

**Why this priority**: é a base de todas as telas; sem ela, cada tela redesenhada ficaria com um
estilo diferente.

**Independent Test**: abrir uma tela qualquer com o aparelho em modo claro e depois em modo escuro;
os dois mostram o mesmo visual claro, branco com vermelho e dourado, legível, sem nenhum verde.

**Acceptance Scenarios**:

1. **Given** o aparelho em modo claro ou escuro, **When** o usuário abre o sistema, **Then** todas as
   telas aparecem no visual claro: branco, vermelho como cor principal e dourado nos detalhes.
2. **Given** qualquer tela, **When** o usuário a percorre, **Then** não há verde em lugar nenhum;
   sucesso e confirmações usam o dourado, sempre com ícone ou texto.
3. **Given** o sistema aberto, **When** o usuário troca o modo do aparelho, **Then** nada muda na
   aparência e nada se perde.
4. **Given** um aparelho configurado para reduzir transparência ou movimento, **When** o usuário
   abre o sistema, **Then** as superfícies aparecem sólidas e sem animações de lente, mantendo o
   mesmo estilo de cores, cantos e espaçamentos.
5. **Given** um navegador que não desenha o efeito completo de vidro (ex.: Safari do iPhone), **When** o
   usuário abre o sistema, **Then** as superfícies aparecem como vidro fosco (desfocado, sem
   distorção), legíveis e sem nada quebrado; num navegador sem nenhum desfoque, aparecem sólidas.

---

### User Story 2 - A tela do grupo redesenhada (Priority: P1)

A tela do grupo — onde o participante passa a maior parte do tempo — ganha o visual novo: detalhes
do evento, código para compartilhar, confirmação, sorteio, "Seu amigo secreto" e os cartões dos
dependentes, listas de desejos, participantes, pedidos de saída e avisos. Cada parte fica fácil de
achar e de tocar no celular, e o momento de revelar o amigo secreto fica marcante.

**Why this priority**: é a tela mais usada e a que mais importa para a experiência do amigo
secreto.

**Independent Test**: percorrer a tela do grupo nos três status (aguardando sorteio, sorteado e
finalizado), como organizador, participante e responsável por dependentes, a 360px; todas as
partes aparecem no visual novo, sem rolagem horizontal, e todas as ações continuam funcionando.

**Acceptance Scenarios**:

1. **Given** um grupo aguardando sorteio, **When** o participante abre a tela, **Then** vê os
   detalhes do evento, o código, a confirmação, as listas, os dependentes e os participantes no
   visual novo, com as mesmas informações e ações de antes.
2. **Given** um grupo sorteado, **When** o participante toca em "Revelar", **Then** o nome aparece
   com destaque visual (transição suave) e "Ocultar" o esconde de novo; com o nome oculto, nada do
   nome aparece na tela, nem borrado.
3. **Given** qualquer ação da tela (confirmar, sortear, adicionar item, pedir saída…), **When** o
   usuário a executa, **Then** vê o estado de carregamento, de sucesso e de erro no visual novo.
4. **Given** uma janela de confirmação (remover, sortear, pedir saída…), **When** ela abre, **Then**
   aparece como uma superfície de vidro sobre a tela, com o foco do teclado preso nela até fechar.

---

### User Story 3 - Telas de conta e de grupos redesenhadas (Priority: P2)

Entrar, criar conta, "Meus grupos", criar grupo e entrar num grupo pelo código ganham o mesmo
visual, com o cabeçalho e a navegação entre essas telas.

**Why this priority**: são as primeiras telas que a pessoa vê; completam o redesenho, mas o uso
principal acontece na tela do grupo.

**Independent Test**: criar uma conta, entrar, ver "Meus grupos" vazio e com grupos (em andamento e
finalizados), criar um grupo e entrar em outro pelo código, tudo a 360px e a 1280px; tudo
no visual novo e com os mesmos fluxos.

**Acceptance Scenarios**:

1. **Given** a tela de entrar ou de criar conta, **When** o usuário preenche errado, **Then** vê os
   erros de cada campo no visual novo, com rótulos sempre visíveis.
2. **Given** "Meus grupos", **When** o usuário abre a tela, **Then** vê os grupos em cartões do visual
   novo, separados em "Em andamento" e "Finalizados", com o mesmo conteúdo de antes.
3. **Given** os formulários de criar grupo e de entrar com código, **When** o usuário os preenche,
   **Then** os campos (texto, data, horário, número) seguem o visual novo e continuam com as
   validações de antes.
4. **Given** qualquer tela logada no celular, **When** o usuário quer ir para outra parte do sistema,
   **Then** usa a barra de abas embaixo, com a aba atual marcada, e chega aos mesmos lugares de antes;
   no tablet, iPad ou computador, a mesma navegação fica num menu lateral fixo à esquerda.
5. **Given** qualquer tela logada, **When** o usuário toca em "Sair", **Then** vê "Sair da sua conta?" e
   só sai ao confirmar; cancelar o mantém na mesma tela.
6. **Given** a última ação de uma tela comprida (ex.: "Sair do grupo"), **When** o usuário rola até o
   fim, **Then** ela fica inteira acima da barra de abas e pode ser tocada.

---

### User Story 4 - Disposição reorganizada: um desenho para celular e outro para tablet e computador (Priority: P1)

As telas deixam de ser uma coluna longa com tudo empilhado. No celular, cada tela mostra o que
importa sem rolar muito: a tela do grupo ganha abas "Resumo", "Pessoas" e "Desejos". No tablet,
iPad e computador, o espaço é aproveitado: menu lateral, grupos em grade e a tela do grupo em duas
colunas.

**Why this priority**: é o pedido principal da revisão — a disposição atual não agradou e o
sistema precisa ser confortável no celular.

**Independent Test**: percorrer "Meus grupos", a tela do grupo (nos três status, como organizador,
participante e responsável), entrar, criar conta, criar grupo e entrar com código a 360px, 390px,
768px (iPad em pé), 1024px (iPad deitado) e 1280px; em cada largura, a disposição é a do desenho
correspondente, sem rolagem horizontal e com todas as ações funcionando.

**Acceptance Scenarios**:

1. **Given** a tela do grupo no celular, **When** ela abre, **Then** mostra no topo o nome e o status
   do grupo e, logo abaixo, as abas "Resumo", "Pessoas" e "Desejos", com "Resumo" marcada; o
   "Resumo" tem os avisos, a confirmação, o amigo secreto (ou o código e o sorteio, antes do
   sorteio), os detalhes do evento, os pedidos de saída e a ação de sair; "Pessoas" tem os
   participantes e os dependentes; "Desejos" tem a minha lista e as listas dos dependentes.
2. **Given** a tela do grupo no celular, **When** o usuário troca de aba, **Then** só o conteúdo muda,
   sem recarregar, e ao voltar do navegador ou recarregar a página a mesma aba continua aberta.
3. **Given** a tela do grupo no tablet, iPad ou computador, **When** ela abre, **Then** não há abas:
   o resumo fica na coluna principal e pessoas e desejos na coluna lateral, tudo visível ao mesmo
   tempo.
4. **Given** "Meus grupos", **When** a tela abre, **Then** no celular os grupos aparecem em lista com os
   botões "Criar grupo" e "Entrar com código" lado a lado no topo; no tablet e no computador, em
   grade de duas ou três colunas, com os botões ao lado do título.
5. **Given** as telas de entrar e de criar conta, **When** abertas no tablet ou no computador,
   **Then** aparecem em duas metades — à esquerda, a marca do Amigo Secreto em vermelho com detalhes
   dourados; à direita, o formulário; no celular, só o formulário num cartão, com a marca acima.
6. **Given** os formulários de criar grupo e de entrar com código, **When** abertos no tablet ou no
   computador, **Then** ficam num cartão centralizado de largura confortável, sem esticar até a
   borda.
7. **Given** um tablet girado ou uma janela redimensionada, **When** a largura cruza 768px, **Then** a
   disposição troca de desenho sem perder a tela, a aba ou o que estava sendo digitado.

---

### Edge Cases

- **Preferências de acessibilidade do aparelho**: reduzir transparência e reduzir movimento deixam
  as superfícies sólidas e sem animações, mas com o mesmo estilo.
- **Navegador sem suporte ao efeito completo de vidro**: vidro fosco simplificado; sem nenhum
  desfoque, superfícies sólidas — sempre legíveis e com o mesmo contraste; nada some nem fica
  transparente demais.
- **Texto sobre vidro**: o fundo colorido nunca pode baixar o contraste do texto abaixo do mínimo;
  se o fundo atrás variar, a superfície fica opaca o suficiente para manter o contraste.
- **Nomes e textos longos** (grupos, participantes, dependentes, itens de lista, links): quebram
  linha dentro das superfícies, sem estourar a largura de 360px.
- **Girar o tablet ou redimensionar a janela**: ao cruzar a largura de 768px, a navegação passa da
  barra de baixo para o menu lateral (ou o contrário) e a tela do grupo passa das abas para as duas
  colunas, sem perder a tela atual, a aba escolhida nem o que estava sendo digitado.
- **Aba do grupo no endereço**: a aba aberta no celular fica no endereço da página; voltar no
  navegador ou recarregar mantém a aba; um endereço com aba aberto no computador mostra as duas
  colunas normalmente.
- **Teclado aberto no celular**: com um campo em foco, a barra de abas não cobre o campo nem o
  botão de enviar.
- **Vermelho da marca e vermelho de erro**: um erro nunca é mostrado só pela cor; vem com texto e,
  quando couber, ícone.
- **Aparelho em modo escuro**: o sistema continua claro; nenhum componente da biblioteca pode
  aparecer escuro por conta própria.
- **Celulares mais simples**: o efeito de vidro não pode deixar a rolagem lenta; se pesar, o
  sistema usa a versão sólida.
- **Leitores de tela e teclado**: todos os componentes novos têm nome acessível, foco visível e a
  mesma ordem de navegação de antes.
- **Impressão ou captura de tela da revelação**: o sigilo continua sendo da pessoa; o visual não
  deixa nenhum nome oculto legível em lugar nenhum.

## Requirements *(mandatory)*

### Functional Requirements

**Identidade visual**

- **FR-001**: Todas as telas e partes de tela das features 001 a 004 MUST usar o visual novo; nenhuma
  tela pode ficar com a aparência antiga.
- **FR-002**: O sistema MUST usar os componentes da biblioteca escolhida onde ela os oferece
  (botões, interruptores, seletores segmentados, menus, busca e superfícies de vidro) e, para o
  que ela não oferece (campos de formulário, avisos, selos, cartões, janelas), componentes do
  projeto com a mesma aparência.
- **FR-003**: O visual MUST seguir um conjunto único e documentado de cores, cantos, espaçamentos,
  tipografia e sombras, num tema único e claro.
- **FR-004**: O sistema MUST ter um só tema, claro, em qualquer aparelho: o modo escuro do aparelho
  MUST NOT mudar cores, fundo nem componentes (revisão de 2026-11-27).
- **FR-005**: O fundo de todas as telas MUST ser branco, com brilhos suaves e desfocados de vermelho
  e dourado (FR-016) — o bastante para o efeito de vidro aparecer, sem ilustrações e sem atrapalhar
  a leitura; o mesmo fundo em todas as telas.

**Acessibilidade e uso no celular**

- **FR-006**: Todo texto e todo controle MUST atender ao contraste mínimo WCAG 2.1 AA,
  inclusive sobre superfícies de vidro.
- **FR-007**: Todas as telas MUST funcionar a partir de 360px de largura sem rolagem horizontal, com
  áreas de toque de pelo menos 44×44px.
- **FR-008**: Com "reduzir transparência" ou "reduzir movimento" ativados no aparelho, o sistema MUST
  trocar o vidro por superfícies sólidas e tirar as animações, mantendo o restante do estilo.
- **FR-009**: Quando o navegador não conseguir desenhar o efeito completo de vidro (distorção e
  brilho na borda), o sistema MUST mostrar vidro fosco simplificado (desfocado e translúcido, sem
  distorção); quando nem o desfoque funcionar, MUST mostrar superfícies sólidas e legíveis.
- **FR-010**: Todo componente MUST ter foco de teclado visível e nome acessível; as janelas MUST
  prender o foco enquanto abertas e devolvê-lo ao fechar.
- **FR-011**: Formulários MUST manter os rótulos sempre visíveis e as mensagens de erro junto do
  campo, no visual novo.
- **FR-012**: Toda ação assíncrona MUST mostrar carregamento, sucesso e erro no visual novo.

**O que não muda**

- **FR-013**: Textos, telas, rotas, regras de negócio, validações e fluxos das features 001 a 004
  MUST continuar iguais; o número de toques para concluir cada fluxo MUST NOT aumentar, com duas
  exceções: sair da conta ganha a confirmação da FR-015; no celular, chegar a "Pessoas" ou
  "Desejos" na tela do grupo custa um toque na aba (FR-017).
- **FR-014**: O sigilo do resultado MUST continuar igual: com o nome oculto, nada do nome aparece na
  tela, nem borrado ou transparente; nada do resultado vai para o armazenamento do navegador.
- **FR-015**: Nas telas logadas, a navegação MUST oferecer "Meus grupos", "Criar grupo", "Entrar com
  código" e "Sair", levando aos mesmos lugares de hoje, com a opção da tela atual marcada:
  - no celular (larguras menores que a de um tablet em pé, 768px), numa barra de abas fixa embaixo,
    como nos apps do iPhone, que MUST NOT cobrir conteúdo nem botões (o fim de cada tela fica acima
    dela) e MUST respeitar a área segura do aparelho;
  - no tablet, iPad e computador (768px ou mais), num menu lateral fixo à esquerda, de vidro, com a
    marca no topo e "Sair" no fim (revisão de 2026-11-27).
  As telas de entrar e de criar conta não têm essa navegação. "Sair" MUST pedir confirmação ("Sair da
  sua conta?") antes de encerrar a sessão; cancelar mantém a pessoa onde estava.

**Cores**

- **FR-016**: A paleta MUST ser branco e vermelho com detalhes dourados (revisão de 2026-11-27):
  branco nas superfícies e no fundo, vermelho como cor principal de ações e destaques, dourado nos
  detalhes (selos, ícones, filetes, brilhos) e no sucesso. MUST NOT haver verde. O erro não pode ser
  mostrado só pelo vermelho: vem sempre com ícone e texto. Tudo com o contraste da FR-006; o fundo
  da FR-005 usa os mesmos tons.

**Disposição (revisão de 2026-11-27)**

- **FR-017**: O sistema MUST ter dois desenhos, trocados na largura de 768px:
  - **celular** (menos de 768px): uma coluna; a tela do grupo MUST ter, abaixo do nome e do status,
    as abas "Resumo", "Pessoas" e "Desejos" (conteúdo de cada uma na US4, cenário 1), com "Resumo"
    aberta por padrão e a aba escolhida guardada no endereço da página; "Meus grupos" em lista, com
    "Criar grupo" e "Entrar com código" lado a lado no topo; entrar e criar conta num cartão único
    com a marca acima;
  - **tablet, iPad e computador** (768px ou mais): menu lateral (FR-015); a tela do grupo MUST mostrar,
    sem abas, o resumo numa coluna principal e pessoas e desejos numa coluna lateral; "Meus grupos"
    em grade de duas ou três colunas; entrar e criar conta em duas metades (marca à esquerda,
    formulário à direita); criar grupo e entrar com código num cartão centralizado de largura
    limitada.
  Trocar de desenho (girar o tablet, redimensionar) MUST NOT perder a tela, a aba nem o que estava
  sendo digitado.

### Key Entities

Não há dados novos. A feature muda só a apresentação.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% das telas e estados listados no inventário de telas (features 001 a 004) aparecem
  no visual novo, conferidos um a um no celular (360px) e no computador (1280px).
- **SC-002**: 0 falhas de contraste WCAG 2.1 AA numa verificação automática de todas as telas.
- **SC-003**: 0 telas com rolagem horizontal a 360px e 0 controles com área de toque menor que
  44×44px.
- **SC-004**: Em um celular intermediário numa conexão 4G, a primeira tela fica utilizável em até
  3 segundos e a rolagem não engasga com o efeito de vidro.
- **SC-005**: Todos os fluxos das features 001 a 004 continuam funcionando, com o mesmo número de
  toques ou menos (exceto sair da conta, que ganha uma confirmação).
- **SC-006**: Pelo menos 80% das pessoas de um teste rápido com usuários preferem o visual novo ao
  antigo.

## Assumptions

- O redesenho troca a aparência dos componentes que já existem; não cria telas, campos ou ações
  novas. Mudam de lugar a navegação (barra embaixo no celular, menu lateral no tablet e no
  computador — FR-015) e a disposição das partes de cada tela (FR-017).
- A biblioteca escolhida é nova (versão anterior à 1.0, mantida por uma pessoa); o projeto aceita o
  risco de mudanças de versão por ser um pedido explícito do usuário, e as próximas atualizações
  dela seguem a política de dependências do projeto (só por segurança ou necessidade).
- O tema é único e claro (revisão de 2026-11-27); não há tema escuro nem botão para trocar de tema
  nesta versão.
- Ícones, se necessários, seguem o mesmo estilo e não trazem textos novos.
- **Fora do escopo desta versão**: mudanças de texto ou de fluxo, telas novas, temas escolhidos
  pelo usuário, animações de comemoração além da revelação, logotipo novo.
