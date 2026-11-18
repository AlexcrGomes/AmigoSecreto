# Feature Specification: Detalhes do Evento, Confirmação de Participação e Grupo Finalizado

**Feature Branch**: `002-grupo-evento-confirmacao`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "Melhorar o grupo de amigo secreto. O grupo passa a ter uma descrição, e a data e o local onde ocorrerá o evento (a troca de presentes). Cada participante precisa confirmar (dar o aceite) que vai participar, e só depois que todos confirmarem o criador do grupo pode realizar o sorteio — assim todos concordam em participar. Haverá um novo status do grupo para quando o evento já aconteceu: finalizado."

**Depende de**: [001-amigo-secreto-sorteio](../001-amigo-secreto-sorteio/spec.md) — contas, grupos,
entrada por código, sorteio sigiloso e saída/remoção de participantes continuam valendo como lá
descritos, salvo o que esta spec altera.

**Glossário** (complementa o da feature 001):

- **Evento**: o encontro em que os participantes trocam os presentes.
- **Detalhes do evento**: descrição do grupo, data (e horário, se informado) e local do evento.
- **Confirmação**: o aceite de um participante de que concorda em participar do amigo secreto,
  nas condições dos detalhes do evento.
- **Participante pendente**: participante que entrou no grupo, mas ainda não confirmou.
- **Pedido de saída**: pedido de um participante para deixar um grupo já sorteado; depende da
  aprovação do organizador.
- **Situação do grupo**: *aguardando sorteio* → *sorteado* → *finalizado* (nova). Uma saída
  aprovada depois do sorteio faz o grupo voltar de *sorteado* para *aguardando sorteio*.

**Governança**: a US4 desfaz e refaz o sorteio, o que a constituição v1.1.0 (Princípio II) e a
feature 001 (FR-019 e FR-025) proibiam. A constituição v1.2.0 (2026-11-18) passou a permitir
exatamente esta exceção: saída aprovada pelo organizador, pares descartados de forma
irrecuperável e sem reentrada de quem saiu.

## Clarifications

### Session 2026-11-18

- Q: Data e local do evento são obrigatórios na criação do grupo? → A: Sim, obrigatórios na
  criação; o organizador pode alterá-los depois.
- Q: Se o organizador mudar a data ou o local antes do sorteio, as confirmações continuam
  valendo? → A: Não; todos, exceto o organizador, voltam a pendente e precisam confirmar de novo.
- Q: Como o grupo passa para *finalizado*? → A: Automaticamente, depois do evento: no horário
  informado ou, sem horário, ao fim do dia do evento.
- Q (levantada pelo usuário): E se alguém quiser sair de um grupo já sorteado? → A: A pessoa
  pede para sair; o organizador aprova ou recusa. Aprovado, ela sai, o sorteio é desfeito e o
  organizador pode sortear de novo com quem permaneceu.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Informar descrição, data e local do evento (Priority: P1)

Ao criar o grupo, o organizador descreve o amigo secreto (por exemplo, "Amigo secreto da
família, presente até R$ 100") e informa a data e o local da troca de presentes. Todos os
participantes veem esses detalhes ao abrir o grupo. O organizador pode corrigir os detalhes
depois, e quem estiver com o grupo aberto vê a mudança sem recarregar a página.

**Why this priority**: é a informação de que os participantes precisam para decidir se vão
participar; a confirmação (US2) só faz sentido com esses detalhes à vista.

**Independent Test**: o organizador cria um grupo com descrição, data e local; outro usuário entra
pelo código e vê os mesmos detalhes; o organizador altera o local e o outro usuário vê o novo
local sem recarregar.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado, **When** ele cria um grupo informando nome, descrição, data
   e local, **Then** o grupo é criado e os detalhes aparecem na tela do grupo.
2. **Given** a criação de um grupo, **When** o organizador informa uma data que já passou,
   **Then** o sistema recusa e explica que a data do evento precisa ser hoje ou no futuro.
3. **Given** um participante do grupo, **When** ele abre o grupo, **Then** vê a descrição, a data
   (com horário, se informado) e o local do evento.
4. **Given** o organizador, **When** ele altera a descrição, a data ou o local, **Then** a
   alteração é salva e quem está com o grupo aberto a vê sem recarregar a página.
5. **Given** um participante que não é o organizador, **When** ele abre o grupo, **Then** não tem
   a opção de alterar os detalhes do evento.
6. **Given** a tela inicial, **When** o usuário vê a lista dos seus grupos, **Then** cada grupo
   mostra também a data do evento.

---

### User Story 2 - Confirmar participação antes do sorteio (Priority: P1)

Quem entra no grupo pelo código fica como *pendente* até confirmar que vai participar. Ao abrir o
grupo, o participante pendente vê os detalhes do evento e o botão "Confirmar participação"; se não
quiser participar, sai do grupo (como já é possível hoje). Todos os membros veem quem já confirmou
e quem ainda falta. O organizador só consegue sortear quando todos os participantes confirmaram.

**Why this priority**: é o pedido central desta feature: ninguém entra no sorteio sem ter
concordado em participar.

**Independent Test**: com um grupo de 3 participantes em que um ainda não confirmou, o
organizador não consegue sortear e vê quem falta; esse participante confirma e o sorteio é
liberado.

**Acceptance Scenarios**:

1. **Given** um usuário que acabou de entrar no grupo pelo código, **When** ele abre o grupo,
   **Then** aparece como pendente e vê os detalhes do evento e o botão "Confirmar participação".
2. **Given** um participante pendente, **When** ele confirma, **Then** passa a aparecer como
   confirmado para todos os membros, sem que precisem recarregar a página.
3. **Given** o organizador que acabou de criar o grupo, **When** o grupo é criado, **Then** ele já
   conta como confirmado.
4. **Given** um grupo com pelo menos 3 participantes e algum pendente, **When** o organizador tenta
   sortear, **Then** o sistema impede e mostra quantos e quais participantes ainda não
   confirmaram.
5. **Given** um grupo com pelo menos 3 participantes, todos confirmados, **When** o organizador
   sorteia, **Then** o sorteio acontece como na feature 001.
6. **Given** um participante pendente que não quer participar, **When** ele escolhe sair do grupo,
   **Then** deixa o grupo e o sorteio pode ser liberado se todos os demais já confirmaram.
7. **Given** o organizador e um participante pendente, **When** o organizador remove esse
   participante, **Then** ele deixa o grupo, como na feature 001.
8. **Given** um grupo com todos confirmados, **When** um novo usuário entra pelo código, **Then** o
   sorteio volta a ficar bloqueado até essa pessoa confirmar.
9. **Given** o organizador alterou a data ou o local do evento antes do sorteio, **When** a
   alteração é salva, **Then** todos os participantes, exceto o organizador, voltam a pendente,
   são avisados de que precisam confirmar de novo, e o sorteio fica bloqueado até lá.
10. **Given** o organizador alterou apenas a descrição, **When** a alteração é salva, **Then** as
    confirmações continuam valendo.

---

### User Story 3 - Grupo finalizado depois do evento (Priority: P2)

Depois que o evento acontece, o grupo passa para a situação *finalizado*. Ele continua aparecendo
para os participantes, separado dos grupos em andamento, e cada um ainda pode rever quem tirou,
mas nada mais pode ser alterado.

**Why this priority**: organiza a tela inicial e encerra o ciclo do amigo secreto, mas o fluxo
principal (detalhes, confirmação e sorteio) funciona sem ela.

**Independent Test**: um grupo sorteado cujo evento já aconteceu aparece como finalizado na tela
inicial, em uma seção separada; o participante abre o grupo, revela o próprio amigo secreto e não
encontra opção de alterar nada.

**Acceptance Scenarios**:

1. **Given** um grupo sorteado, **When** o evento já aconteceu, **Then** o grupo passa a
   *finalizado* automaticamente, sem ação de ninguém: no horário do evento, se informado, ou ao
   fim do dia do evento.
2. **Given** um grupo finalizado, **When** um participante abre a tela inicial, **Then** o grupo
   aparece em uma seção separada dos grupos em andamento.
3. **Given** um grupo finalizado, **When** um participante abre o grupo, **Then** vê os detalhes
   do evento e ainda pode revelar o próprio amigo secreto, só o dele.
4. **Given** um grupo finalizado, **When** alguém tenta alterar os detalhes, entrar, sair, remover
   participante ou confirmar, **Then** o sistema impede, informando que o grupo foi finalizado.
5. **Given** um grupo ainda não sorteado, **When** a data do evento passa, **Then** o grupo NÃO é
   finalizado e o organizador é avisado de que precisa atualizar a data para poder sortear.

---

### User Story 4 - Sair de um grupo já sorteado, com aprovação do organizador (Priority: P2)

Depois do sorteio, um participante que não poderá mais participar pede para sair. O organizador
vê o pedido e decide: se recusar, nada muda; se aprovar, a pessoa sai do grupo, o sorteio é
desfeito para todos e o grupo volta a *aguardando sorteio*. Então o organizador sorteia de novo
entre quem permaneceu, e cada um recebe um novo amigo secreto.

**Why this priority**: resolve um imprevisto real (alguém que desiste depois do sorteio) sem
obrigar a criar outro grupo, mas o fluxo principal funciona sem isso.

**Independent Test**: em um grupo sorteado com 4 participantes, um deles pede para sair; o
organizador aprova; o grupo volta a *aguardando sorteio*, ninguém vê mais o amigo secreto antigo;
o organizador sorteia de novo e os 3 que ficaram recebem novos amigos secretos, só entre eles.

**Acceptance Scenarios**:

1. **Given** um grupo sorteado, **When** um participante que não é o organizador pede para sair,
   **Then** o pedido fica pendente, o organizador vê o pedido e o sorteio continua valendo
   enquanto isso.
2. **Given** um pedido pendente, **When** o próprio participante o cancela, **Then** o pedido
   deixa de existir e nada muda no grupo.
3. **Given** um pedido pendente, **When** o organizador recusa, **Then** o participante continua no
   grupo com o mesmo amigo secreto e é informado de que o pedido foi recusado.
4. **Given** um pedido pendente, **When** o organizador aprova, **Then** o participante sai do grupo
   e perde o acesso a ele, o sorteio é desfeito para todos (ninguém consegue mais ver o amigo
   secreto anterior), o grupo volta a *aguardando sorteio* e todos com o grupo aberto são
   avisados sem recarregar a página.
5. **Given** um sorteio desfeito e pelo menos 3 participantes, todos confirmados, **When** o
   organizador sorteia de novo, **Then** acontece um novo sorteio, só entre quem permaneceu, com
   todas as regras da feature 001.
6. **Given** um sorteio desfeito e menos de 3 participantes, **When** o organizador tenta sortear,
   **Then** o sistema impede até que novos participantes entrem e confirmem.
7. **Given** o organizador de um grupo sorteado, **When** ele procura a opção de sair, **Then** ela
   não existe, como na feature 001.
8. **Given** uma pessoa que saiu de um grupo depois do sorteio, **When** ela tenta entrar de novo
   com o código, **Then** a entrada é recusada.

---

### Edge Cases

- **Participante entra no exato momento do sorteio**: o sorteio só acontece se, no instante em
  que é processado, todos os participantes do grupo estiverem confirmados; caso contrário é
  recusado e o organizador vê quem falta.
- **Confirmação repetida** (dois toques, duas abas): a pessoa continua confirmada uma única vez;
  não há erro.
- **Confirmar depois de sair ou ser removido**: o sistema recusa, pois a pessoa já não é
  participante.
- **Data do evento passou sem sorteio**: o sorteio fica bloqueado até o organizador informar uma
  nova data (hoje ou no futuro); confirmações e participantes são mantidos.
- **Data do evento hoje**: é permitida; o sorteio pode ser feito no próprio dia.
- **Horário não informado**: a data é exibida sem horário; o evento é considerado acontecido ao
  fim desse dia.
- **Descrição com quebras de linha ou texto longo**: as quebras de linha são preservadas na
  exibição; textos acima do limite são recusados com mensagem clara.
- **Descrição ou local com marcação/HTML**: o texto é exibido exatamente como digitado, nunca
  interpretado como conteúdo da página.
- **Grupos criados antes desta feature** (aguardando sorteio): ficam sem detalhes do evento e com
  os participantes não organizadores pendentes; o organizador precisa preencher data e local e
  todos precisam confirmar antes do sorteio. Grupos já sorteados continuam sorteados.
- **Fuso horário**: datas e horários são exibidos no horário de Brasília.
- **Dois pedidos de saída pendentes ao mesmo tempo**: ao aprovar um deles, o sorteio é desfeito e
  os demais pedidos são cancelados; essas pessoas são avisadas de que agora podem sair
  diretamente, como antes do sorteio.
- **Aprovação e novo sorteio ao mesmo tempo** (cliques repetidos, duas abas): o pedido é aprovado
  uma única vez e o novo sorteio só acontece depois de o anterior ter sido desfeito por completo.
- **Participante com o amigo secreto revelado na tela quando o sorteio é desfeito**: o nome some
  e ele vê o aviso de que o sorteio foi desfeito e será refeito.
- **Pedido pendente quando o evento acontece**: o grupo é finalizado e o pedido é cancelado.
- **Pedido pendente e organizador altera os detalhes**: o pedido continua pendente; nada muda no
  sorteio (FR-016).

## Requirements *(mandatory)*

### Functional Requirements

**Detalhes do evento**

- **FR-001**: Ao criar um grupo, o organizador MUST poder informar, além do nome: descrição
  (opcional, até 500 caracteres), data do evento, horário do evento (opcional) e local do evento
  (até 120 caracteres, texto livre — ex.: endereço, "casa da vó", "online").
- **FR-002**: Data e local do evento MUST ser obrigatórios na criação do grupo; o organizador
  pode alterá-los depois (FR-005).
- **FR-003**: A data do evento MUST ser hoje ou uma data futura no momento em que é informada ou
  alterada.
- **FR-004**: Todos os participantes MUST ver a descrição, a data (com horário, se houver) e o
  local do evento na tela do grupo; a tela inicial MUST mostrar a data do evento de cada grupo.
- **FR-005**: Somente o organizador MUST poder alterar descrição, data, horário e local, nas
  situações *aguardando sorteio* e *sorteado*; em grupo *finalizado* nada pode ser alterado.
- **FR-006**: Alterações nos detalhes MUST aparecer para quem está com o grupo aberto sem
  recarregar a página.
- **FR-007**: Descrição e local MUST ser exibidos como texto simples, preservando quebras de linha
  da descrição e sem interpretar marcação.

**Confirmação de participação**

- **FR-008**: Todo participante MUST ter uma situação de confirmação: *pendente* ou *confirmado*.
  Quem entra pelo código começa *pendente*; o organizador começa *confirmado* ao criar o grupo.
- **FR-009**: Um participante pendente MUST poder confirmar a própria participação; ninguém pode
  confirmar por outra pessoa, nem o organizador.
- **FR-010**: Confirmar MUST ser idempotente: confirmar de novo não muda nada nem gera erro.
- **FR-011**: Quem não quiser participar MUST poder sair do grupo antes do sorteio (regra da
  feature 001); não existe uma situação "recusado" separada.
- **FR-012**: Todos os membros MUST ver, na lista de participantes, quem está confirmado e quem
  está pendente, e a contagem de confirmados sobre o total; a lista MUST ser atualizada sem
  recarregar a página quando alguém confirma.
- **FR-013**: O sorteio MUST ser liberado somente quando, no momento em que é processado, o grupo
  tem pelo menos 3 participantes, todos confirmados, e a data do evento não passou. Caso
  contrário, o sistema MUST recusar e informar o motivo (quem falta confirmar, ou que a data
  precisa ser atualizada).
- **FR-014**: O organizador MUST ver, antes de sortear, quantos e quais participantes ainda estão
  pendentes; o botão de sortear MUST indicar por que está bloqueado.
- **FR-015**: Se o organizador alterar a data, o horário ou o local antes do sorteio, a
  confirmação de todos os participantes, exceto a do organizador, MUST voltar a pendente, e quem
  está com o grupo aberto MUST ser avisado de que precisa confirmar de novo. Alterar só a
  descrição não afeta as confirmações.
- **FR-016**: Após o sorteio, a confirmação deixa de ser exigida ou alterável; alterações nos
  detalhes do evento não afetam o sorteio já feito.

**Grupo finalizado**

- **FR-017**: O grupo MUST ter a nova situação *finalizado*, alcançável somente a partir de
  *sorteado*, automaticamente quando o evento acontece: no horário do evento, se informado, ou
  ao fim do dia do evento (horário de Brasília).
- **FR-018**: Um grupo *finalizado* MUST ficar somente para leitura: sem alterar detalhes, entrar,
  sair, remover participante, confirmar ou sortear.
- **FR-019**: Em um grupo *finalizado*, cada participante MUST continuar podendo revelar apenas o
  próprio amigo secreto, com as mesmas regras de sigilo da feature 001.
- **FR-020**: A tela inicial MUST separar os grupos finalizados dos grupos em andamento.
- **FR-021**: A mudança para *finalizado* MUST aparecer para quem está com o grupo aberto sem
  recarregar a página.

**Sigilo, segurança e experiência** (reforçam a feature 001)

- **FR-022**: A situação de confirmação dos participantes e os detalhes do evento MUST ser
  visíveis apenas para membros do grupo; quem não é membro continua recebendo "grupo não
  encontrado".
- **FR-023**: Nenhuma informação nova (detalhes, confirmações, finalização) MUST revelar ou
  permitir deduzir quem tirou quem.
- **FR-024**: Todas as novas telas e campos MUST funcionar a partir de 360px de largura, em
  português do Brasil, com estados de carregamento, sucesso e erro.

**Saída depois do sorteio** (altera as FR-019 e FR-025 da feature 001)

- **FR-025**: Em um grupo *sorteado*, um participante (exceto o organizador) MUST poder pedir para
  sair; cada participante tem no máximo um pedido pendente por vez e MUST poder cancelá-lo
  enquanto estiver pendente.
- **FR-026**: Somente o organizador MUST poder aprovar ou recusar pedidos de saída; ele MUST ver os
  pedidos pendentes na tela do grupo, e o solicitante MUST ser informado da decisão.
- **FR-027**: Enquanto um pedido estiver pendente, o sorteio MUST continuar valendo e cada
  participante continua vendo o próprio amigo secreto.
- **FR-028**: Ao aprovar um pedido, o sistema MUST, de uma só vez: retirar o participante do
  grupo; descartar de forma definitiva todos os pares do sorteio, sem que ninguém possa
  recuperá-los; voltar o grupo a *aguardando sorteio*; cancelar os demais pedidos pendentes; e
  avisar quem está com o grupo aberto, sem recarregar a página. As confirmações de quem
  permaneceu continuam valendo.
- **FR-029**: Depois de desfeito, o grupo MUST poder ser sorteado de novo pelo organizador nas
  mesmas condições da FR-013; o novo resultado MUST ser independente do anterior.
- **FR-030**: Quem saiu de um grupo depois do sorteio MUST NOT poder entrar nele de novo.
- **FR-031**: O organizador MUST NOT poder remover participantes nem refazer o sorteio por conta
  própria depois do sorteio; a única forma de desfazer um sorteio é aprovar um pedido de saída.
- **FR-032**: Pedidos de saída e avisos de sorteio desfeito MUST NOT revelar quem tirou quem,
  nem mesmo quem a pessoa que saiu havia tirado.
- **FR-033**: Quando o grupo é finalizado, os pedidos de saída pendentes MUST ser cancelados.

### Key Entities

- **Grupo** (alterado): ganha descrição, data do evento, horário do evento (opcional), local do
  evento e a nova situação *finalizado* (situações: aguardando sorteio / sorteado / finalizado),
  além da data em que foi finalizado.
- **Participação** (alterada): ganha a situação de confirmação (*pendente* / *confirmado*) e a
  data da confirmação.
- **Pedido de saída** (novo): participante que pede para sair, grupo, data do pedido, situação
  (*pendente* / *aprovado* / *recusado* / *cancelado*) e data da decisão. Só existe para grupos
  sorteados.
- **Saída após sorteio** (novo): registro de que uma pessoa saiu de um grupo depois do sorteio,
  usado para impedir que ela entre de novo (FR-030).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: O organizador cria um grupo com descrição, data e local em menos de 1 minuto pelo
  celular.
- **SC-002**: Um participante que abre o grupo pela primeira vez confirma a participação com um
  único toque, em menos de 15 segundos.
- **SC-003**: Nos testes, 100% das tentativas de sorteio com algum participante pendente, com
  menos de 3 participantes ou com a data do evento vencida são recusadas, inclusive quando
  alguém entra no grupo ao mesmo tempo que o sorteio é pedido.
- **SC-004**: Quando um participante confirma ou o organizador altera os detalhes, quem está com o
  grupo aberto vê a mudança em até 5 segundos, sem recarregar.
- **SC-005**: Nos testes de segurança, 0 tentativas de não membros de ver detalhes ou confirmações
  de um grupo têm sucesso, e 0 tentativas de confirmar por outra pessoa têm sucesso.
- **SC-006**: Grupos finalizados aparecem separados na tela inicial e 100% das tentativas de
  alterá-los são recusadas.
- **SC-007**: Pelo menos 90% dos participantes de um teste com usuários entendem, sem ajuda, que
  precisam confirmar a participação e conseguem fazê-lo na primeira tentativa.
- **SC-008**: Nos testes, depois de aprovada uma saída, 100% das tentativas de ver um amigo
  secreto do sorteio desfeito falham, e o novo sorteio inclui exatamente quem permaneceu.
- **SC-009**: O organizador aprova ou recusa um pedido de saída com um toque e a confirmação, em
  menos de 15 segundos a partir da tela do grupo.

## Assumptions

- O organizador confirma automaticamente ao criar o grupo: criar o grupo já é o aceite dele.
- Não existe "recusar convite": quem não quer participar sai do grupo (feature 001, US4).
- Um participante confirmado não pode "desconfirmar"; se mudar de ideia antes do sorteio, sai do
  grupo.
- O horário do evento é opcional; sem horário, o evento é considerado acontecido ao fim do dia
  informado.
- Datas e horários são exibidos no horário de Brasília (America/Sao_Paulo) e armazenados em UTC,
  conforme a constituição.
- Não há limite máximo para a data do evento além de ser hoje ou futura; o mínimo de 3 e o máximo
  de 100 participantes da feature 001 continuam valendo.
- Desfazer o sorteio por uma saída aprovada não exige nova confirmação de quem permaneceu: as
  condições do evento não mudaram.
- Se alguém já tiver comprado o presente, a troca de amigo secreto após um novo sorteio é
  combinada entre os participantes, fora do sistema.
- Os detalhes do evento podem ser alterados depois do sorteio (por exemplo, mudança de local),
  mas isso não reabre confirmações nem afeta o sorteio.
- O sistema não envia e-mails nem notificações push sobre confirmações, mudanças nos detalhes ou
  finalização; os avisos aparecem só dentro do sistema, para quem está com ele aberto.
- **Fora do escopo desta versão**: lembretes da data do evento, mapa ou geolocalização do local,
  valor sugerido do presente como campo próprio (pode ir na descrição), lista de desejos, prazo
  para confirmar, convidar por e-mail, reabrir um grupo finalizado, excluir grupo, motivo do
  pedido de saída, o organizador remover alguém depois do sorteio, o organizador sair do grupo.
