# Feature Specification: Lista de Desejos

**Feature Branch**: `003-lista-desejos`

**Created**: 2026-11-23

**Status**: Draft

**Input**: User description: "Lista de desejos no grupo de amigo secreto. Cada participante pode cadastrar, no grupo, uma lista de sugestões de presente (itens com descrição e, opcionalmente, um link e uma faixa de preço). Depois do sorteio, quem tirou a pessoa vê a lista de desejos dela, e só dela — junto com o nome revelado. Ninguém mais vê a lista de outra pessoa, mantendo o sigilo de quem tirou quem. O participante pode editar a própria lista a qualquer momento enquanto o grupo não estiver finalizado."

**Depende de**: [001-amigo-secreto-sorteio](../001-amigo-secreto-sorteio/spec.md) (contas, grupos,
sorteio sigiloso, "Seu amigo secreto" com Revelar/Ocultar) e
[002-grupo-evento-confirmacao](../002-grupo-evento-confirmacao/spec.md) (grupo finalizado, saída
depois do sorteio e sorteio desfeito). Tudo o que lá está continua valendo, salvo o que esta spec
acrescenta.

**Glossário** (complementa o das features 001 e 002):

- **Lista de desejos**: as sugestões de presente que um participante cadastra para si, em um
  grupo. Cada participante tem no máximo uma lista por grupo.
- **Item**: uma sugestão da lista — descrição obrigatória e, se quiser, um link.
- **Valor do presente**: o preço médio combinado para os presentes do grupo, informado pelo
  organizador e visível a todos os participantes. Cada grupo tem o seu.
- **Dono da lista**: o participante que cadastrou a lista; só ele a altera.
- **Quem tirou**: o participante que tirou o dono da lista no sorteio — o único, além do dono,
  que pode ver a lista.

**Sigilo (Constituição, Princípio I)**: a lista é um caminho novo até um amigo secreto. Ver a
lista de alguém é o mesmo que saber quem essa pessoa tirou, então a lista de uma pessoa só pode
aparecer junto do resultado de quem a tirou, pelo mesmo caminho que hoje revela o nome — e para
mais ninguém, nem o organizador.

## Clarifications

### Session 2026-11-23

- Q: Quando o dono altera a lista depois do sorteio, como quem o tirou deve ficar sabendo? → A: Ao
  revelar, a lista mostra "Atualizada em dd/mm" quando mudou depois do sorteio, sem aviso ao vivo.
- Q: Como o dono deve informar a faixa de preço de um item? → A: Os itens não têm faixa de preço.
  O preço médio do presente é definido no grupo, para que todos saibam quanto podem gastar e
  esperar receber; cada grupo pode ter um valor diferente, por isso a lista é vinculada a cada
  grupo. A lista tem no máximo 5 itens.
- Q: Se o organizador mudar o valor do presente depois que os participantes confirmaram, o que
  acontece com as confirmações? → A: O valor só pode ser informado ou alterado antes do sorteio;
  depois do sorteio, não pode mais ser alterado. Mudar o valor não pede nova confirmação.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Montar a própria lista de desejos (Priority: P1)

O participante abre o grupo e cadastra até 5 coisas que gostaria de ganhar: uma descrição de
cada item e, se quiser, um link de onde encontrar. O valor de referência é o do grupo, então os
itens não têm preço próprio. Ele pode acrescentar, editar e remover itens a qualquer momento,
antes ou depois do sorteio, enquanto o grupo não for finalizado. Só ele vê a própria lista na
tela do grupo.

**Why this priority**: sem lista não há o que mostrar a quem tirou a pessoa; é a base da
funcionalidade e já ajuda o participante a organizar as próprias ideias.

**Independent Test**: um participante cadastra três itens (dois só com descrição e um com link),
edita um e remove outro; ao reabrir o grupo, vê a lista atualizada. Outro participante do mesmo
grupo não vê nada dessa lista.

**Acceptance Scenarios**:

1. **Given** um participante de um grupo aguardando sorteio ou sorteado, **When** ele adiciona um
   item com descrição, **Then** o item aparece na lista dele, na ordem em que foi cadastrado.
2. **Given** um item da lista, **When** o dono altera a descrição ou o link, **Then** a lista
   mostra os valores novos.
3. **Given** um item da lista, **When** o dono o remove, com confirmação, **Then** o item some da
   lista.
4. **Given** um item sem descrição ou com link que não seja um endereço web válido, **When** o dono
   tenta salvar, **Then** o sistema impede e explica o que corrigir.
5. **Given** a lista com 5 itens, **When** o dono tenta adicionar outro, **Then** o sistema impede e
   informa que a lista tem no máximo 5 sugestões.
6. **Given** um grupo finalizado, **When** o dono abre a própria lista, **Then** ele a vê, mas não
   consegue mais alterá-la.
7. **Given** dois participantes do mesmo grupo, **When** um deles abre o grupo, **Then** ele vê
   apenas a própria lista, nunca a do outro — inclusive se for o organizador.

---

### User Story 2 - Ver a lista de desejos de quem tirei (Priority: P1)

Depois do sorteio, ao tocar em "Revelar", o participante vê o nome de quem tirou e, logo abaixo,
a lista de desejos dessa pessoa. Ele pode abrir os links dos itens para ver o presente. Se a
pessoa ainda não tiver cadastrado nada, ele vê um aviso de que a lista está vazia. Ao tocar em
"Ocultar", o nome e a lista somem juntos.

**Why this priority**: é o motivo da funcionalidade — ajudar quem vai comprar o presente a
acertar — e é onde mora o risco de quebrar o sigilo, então precisa ser entregue com o mesmo
cuidado do resultado do sorteio.

**Independent Test**: em um grupo sorteado com 3 participantes, cada um com uma lista diferente,
cada participante revela o próprio amigo secreto e vê exatamente a lista dessa pessoa; nenhuma
outra tela, resposta ou aviso mostra a lista de alguém para quem não a tirou.

**Acceptance Scenarios**:

1. **Given** um grupo sorteado, **When** o participante toca em "Revelar", **Then** ele vê o nome
   de quem tirou, a lista de desejos dessa pessoa (descrição e link de cada item) e o valor do
   presente do grupo.
2. **Given** que a pessoa tirada não cadastrou itens, **When** o participante revela, **Then** ele
   vê o nome e o aviso "Ainda não há sugestões de presente".
3. **Given** o nome e a lista revelados, **When** o participante toca em "Ocultar", **Then** o nome
   e a lista somem juntos da tela.
4. **Given** que o dono alterou a lista depois do sorteio, **When** quem o tirou revela de novo ou
   reabre o grupo, **Then** vê a versão mais recente da lista com a indicação "Atualizada em dd/mm"
   (data da última alteração); se a lista não mudou depois do sorteio, a indicação não aparece.
5. **Given** um item com link, **When** quem tirou o dono toca no link, **Then** o site abre fora
   do sistema, com o aviso de que é um site externo, e o site não recebe de qual página veio.
6. **Given** um grupo aguardando sorteio, **When** qualquer participante procura a lista de outra
   pessoa, **Then** não existe forma de vê-la.
7. **Given** um grupo finalizado, **When** o participante revela o próprio amigo secreto, **Then**
   continua vendo o nome e a lista dessa pessoa, como ela estava ao ser finalizado.

---

### User Story 3 - Valor do presente definido no grupo (Priority: P2)

O organizador informa, nos detalhes do grupo, o valor médio dos presentes, e pode ajustá-lo até o
sorteio. Todos os participantes veem esse valor na tela do grupo antes de confirmar e, depois do
sorteio, junto da lista de quem tiraram — assim sabem quanto podem gastar e o que esperar
receber. Depois do sorteio, o valor fica fixo.

**Why this priority**: sem preço nos itens, o valor do grupo é a referência de quem vai comprar;
mas a lista já é útil sem ele (o valor ainda pode ser combinado na descrição do evento).

**Independent Test**: o organizador informa R$ 80 ao criar o grupo; um participante que entra pelo
código vê "Valor do presente: R$ 80" na tela do grupo; depois do sorteio, o valor aparece junto da
lista revelada. Antes do sorteio, o organizador altera para R$ 100: todos passam a ver o novo valor
e as confirmações continuam valendo; depois do sorteio, não há como alterar.

**Acceptance Scenarios**:

1. **Given** o organizador criando o grupo ou editando um grupo aguardando sorteio, **When** ele
   informa o valor do presente, **Then** todos os participantes veem o valor na tela do grupo.
2. **Given** participantes que já confirmaram, **When** o organizador altera o valor antes do
   sorteio, **Then** o valor novo aparece para todos e as confirmações continuam valendo.
3. **Given** um grupo sem valor informado, **When** um participante abre o grupo, **Then** a tela
   não mostra valor e nada mais muda.
4. **Given** um valor inválido (zero, negativo, com centavos ou acima do limite), **When** o
   organizador tenta salvar, **Then** o sistema impede e explica o que corrigir.
5. **Given** um participante que não é o organizador, **When** ele procura alterar o valor,
   **Then** a opção não existe.
6. **Given** um grupo sorteado ou finalizado, **When** o organizador tenta alterar o valor,
   **Then** o sistema impede e informa que o valor não muda depois do sorteio; os demais detalhes
   do evento continuam editáveis como na feature 002.

---

### Edge Cases

- **Sorteio desfeito por uma saída aprovada** (feature 002): quem tirava a pessoa perde o acesso
  à lista dela no mesmo instante em que perde o nome; depois do novo sorteio, quem a tirar passa a
  ver a lista. As listas de quem permaneceu continuam como estavam.
- **Quem sai do grupo** (antes do sorteio, removido pelo organizador ou com a saída aprovada
  depois do sorteio): a lista dessa pessoa naquele grupo é apagada junto com a participação.
- **Lista com o nome revelado na tela quando o sorteio é desfeito**: o nome e a lista somem
  juntos, como o nome some hoje.
- **Dono altera a lista enquanto quem o tirou está com ela aberta**: quem tirou vê a versão nova,
  com "Atualizada em dd/mm", quando revelar de novo ou reabrir o grupo; ele não recebe aviso ao vivo
  da alteração, para que nenhum sinal do sistema dependa de quem tirou quem.
- **O dono tenta descobrir quem o tirou**: o sistema não informa ao dono se alguém viu a lista,
  quando nem quantas vezes.
- **Link malicioso ou de rastreamento**: o link é texto digitado pelo dono e pode levar a qualquer
  site. O sistema só aceita endereços web (http ou https), mostra o endereço antes de abrir, avisa
  que é um site externo e não envia ao site a página de origem. Abrir o link é sempre escolha de
  quem tirou o dono.
- **Descrição ou link com código (HTML, scripts)**: aparecem como texto, nunca são interpretados
  (como a descrição do evento na feature 002).
- **Participante em vários grupos**: cada grupo tem uma lista independente; alterar a lista de um
  grupo não muda as dos outros.
- **Grupo antigo, criado antes desta feature**: começa com as listas vazias e sem valor do
  presente; os participantes podem cadastrar itens se o grupo não estiver finalizado, e o
  organizador pode informar o valor se o grupo ainda não foi sorteado.
- **Sorteio desfeito e valor do presente**: com o grupo de volta a aguardando sorteio (feature 002),
  o organizador pode alterar o valor de novo até o novo sorteio.
- **Duas abas editando a mesma lista**: cada alteração vale para o item alterado; a última
  gravação de um mesmo item prevalece e a tela mostra a lista atualizada.

## Requirements *(mandatory)*

### Functional Requirements

**Lista do próprio participante**

- **FR-001**: Cada participante de um grupo MUST poder ter uma lista de desejos vinculada a esse
  grupo, com até 5 itens; listas de grupos diferentes são independentes.
- **FR-002**: Cada item MUST ter uma descrição (1 a 120 caracteres) e PODE ter um link. Itens MUST
  NOT ter preço próprio: a referência de preço é o valor do presente do grupo (FR-020).
- **FR-003**: O link, quando informado, MUST ser um endereço web começando com `http://` ou
  `https://`, com até 500 caracteres.
- **FR-005**: O dono MUST poder adicionar, editar e remover itens da própria lista enquanto o grupo
  estiver aguardando sorteio ou sorteado; em um grupo finalizado, a lista MUST ficar somente para
  leitura.
- **FR-006**: Somente o dono MUST poder alterar a própria lista; ninguém altera a lista de outra
  pessoa, nem o organizador.
- **FR-007**: Os itens MUST aparecer na ordem em que foram cadastrados.
- **FR-008**: Descrição e link MUST ser exibidos sempre como texto simples, nunca interpretados
  como formatação ou código.

**Quem pode ver a lista**

- **FR-009**: A lista de um participante MUST ser visível somente para o próprio dono e, depois do
  sorteio, para quem o tirou. Nenhum outro participante, nem o organizador, MUST conseguir vê-la,
  por nenhuma tela, resposta, aviso em tempo real, mensagem de erro ou registro.
- **FR-010**: Quem tirou o dono MUST ver a lista somente junto do resultado do próprio sorteio
  ("Seu amigo secreto"), pelo mesmo caminho que revela o nome; o sistema MUST decidir qual lista
  mostrar só a partir do usuário da sessão, nunca de um identificador enviado pela tela.
- **FR-011**: O nome e a lista MUST aparecer e sumir juntos (Revelar/Ocultar), e a lista MUST NOT
  ficar guardada no aparelho depois de ocultada, como o nome.
- **FR-012**: Com o grupo aguardando sorteio, MUST NOT existir forma de ver a lista de outra pessoa.
- **FR-013**: Quando o sorteio é desfeito (feature 002), quem tirava a pessoa MUST perder o acesso à
  lista dela imediatamente; depois de um novo sorteio, só quem a tirar passa a vê-la.
- **FR-014**: Em um grupo finalizado, quem tirou o dono MUST continuar vendo a lista dele junto do
  nome.
- **FR-015**: O sistema MUST NOT informar ao dono se, quando ou quantas vezes alguém viu a lista
  dele, nem enviar aviso em tempo real a quem o tirou quando a lista mudar.
- **FR-015a**: Quando a lista foi alterada depois do sorteio, ela MUST mostrar a quem tirou o dono,
  junto dos itens, a indicação "Atualizada em dd/mm" com a data da última alteração (horário de
  Brasília); se não houve alteração depois do sorteio, a indicação MUST NOT aparecer.
- **FR-016**: Saber se uma pessoa tem ou não itens na lista MUST ser possível apenas para o dono e
  para quem o tirou.

**Links externos**

- **FR-017**: Ao abrir o link de um item, o sistema MUST abri-lo fora da tela do grupo, avisar que
  é um site externo e MUST NOT informar ao site de qual página o usuário veio.

**Participação e ciclo de vida**

- **FR-018**: A lista de um participante MUST ser apagada quando ele deixa de participar do grupo
  (saiu, foi removido ou teve a saída aprovada).
- **FR-019**: Grupos existentes antes desta feature MUST começar com todas as listas vazias.

**Valor do presente no grupo**

- **FR-020**: O grupo MUST poder ter um valor do presente (preço médio combinado), opcional,
  em reais inteiros de 1 a 100.000, informado pelo organizador ao criar o grupo ou ao editar os
  detalhes do evento enquanto o grupo estiver aguardando sorteio.
- **FR-021**: O valor do presente MUST ser visível a todos os participantes na tela do grupo e, depois
  do sorteio, junto da lista de desejos revelada.
- **FR-022**: Somente o organizador MUST poder informar, alterar ou remover o valor do presente, e
  só antes do sorteio; com o grupo sorteado ou finalizado, o valor MUST ficar somente para leitura.
- **FR-023**: Alterar o valor do presente MUST NOT mudar as confirmações dos participantes (diferente
  de data, horário e local na feature 002).

### Key Entities

- **Lista de desejos**: pertence a um participante em um grupo (no máximo uma por par
  participante–grupo); conjunto ordenado de até 5 itens; deixa de existir quando a participação
  acaba.
- **Item da lista**: descrição; link opcional; ordem de cadastro. Sem preço próprio. Não guarda
  nenhuma referência ao sorteio.
- **Grupo** (alterado): ganha o valor do presente, opcional, em reais inteiros.
- A lista guarda a data da última alteração (inclusão, edição ou remoção de item), usada na
  indicação "Atualizada em dd/mm".

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um participante cadastra um item só com descrição em menos de 30 segundos pelo
  celular.
- **SC-002**: Nos testes de segurança, 0 tentativas de ver a lista de outra pessoa têm sucesso —
  antes do sorteio, depois do sorteio por quem não tirou a pessoa, pelo organizador e depois de um
  sorteio desfeito por quem a tirava antes.
- **SC-003**: Nos testes, 100% das revelações mostram a lista exata da pessoa tirada, inclusive a
  versão mais recente depois de uma alteração.
- **SC-004**: Nos testes, 100% das tentativas de alterar a lista de outra pessoa ou de alterar a
  própria lista em um grupo finalizado são recusadas.
- **SC-005**: Nenhum aviso em tempo real, registro ou mensagem de erro do sistema contém itens de
  lista de desejos ou indica quem viu uma lista (verificado nos testes de sigilo).
- **SC-006**: Pelo menos 90% dos participantes de um teste com usuários encontram, sem ajuda, a
  lista de quem tiraram logo depois de revelar o nome.

## Assumptions

- A lista é por grupo, não por conta: o mesmo usuário pode querer presentes diferentes em grupos
  diferentes (família, trabalho).
- O limite de 5 itens deixa a lista objetiva para quem compra; os tamanhos máximos evitam abuso.
- O valor do presente é opcional: grupos antigos e grupos que preferem combinar o valor de outro
  jeito continuam funcionando sem ele.
- O link não é verificado nem pré-visualizado pelo sistema: buscar o site do lado do sistema
  poderia ser usado para atacar outros endereços e não traz ganho para o sigilo.
- Quem tirou o dono não recebe aviso ao vivo de alterações na lista; ele vê a versão nova, com
  "Atualizada em dd/mm", ao revelar de novo. Um aviso ao vivo, mesmo enviado só a ele, criaria um
  sinal que depende de quem tirou quem.
- Não há reordenação manual dos itens nesta versão; a ordem é a de cadastro.
- **Fora do escopo desta versão**: preço por item, marcar um item como "já comprado" ou reservado,
  fotos de itens,
  lista de desejos geral da conta (fora de grupos), sugestões automáticas de presente,
  pré-visualização de links, compartilhar a lista fora do sistema, aviso em tempo real a quem
  tirou quando a lista muda, comentários ou perguntas anônimas ao dono da lista.
