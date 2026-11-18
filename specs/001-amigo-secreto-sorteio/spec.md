# Feature Specification: Amigo Secreto — Grupos e Sorteio Sigiloso

**Feature Branch**: `001-amigo-secreto-sorteio`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "Sistema web de amigo secreto, responsivo para celular, com login. Ao entrar no sistema, o usuário pode criar um grupo, que recebe um código único; os outros participantes entram no grupo usando esse código. Quando todos tiverem entrado, o criador inicia o sorteio e cada participante retira outra pessoa. O sorteio é feito no mesmo momento para todos os usuários do grupo; se algum usuário tirar a si mesmo, o sorteio precisa ser refeito, pois isso não é permitido. Nenhum usuário pode ver quem outro usuário tirou como amigo secreto."

**Glossário**:

- **Organizador**: usuário que criou o grupo. Também é participante do sorteio.
- **Participante**: usuário que faz parte de um grupo (inclui o organizador).
- **Código do grupo**: código curto e único usado para entrar em um grupo.
- **Amigo secreto**: a pessoa que um participante tirou no sorteio e para quem dará o presente.

## Clarifications

### Session 2026-11-12

- Q: Como o sistema sabe que "todos entraram" para liberar o sorteio? → A: Não há número
  esperado; o organizador decide manualmente quando iniciar (mínimo de 3 participantes).
- Q: A recuperação de senha por e-mail entra nesta versão? → A: Não; fica fora do escopo.
- Q: Depois de sorteado, o organizador pode cancelar e refazer o sorteio? → A: Não; o resultado
  é definitivo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar conta e acessar o sistema (Priority: P1)

Uma pessoa acessa o sistema pelo celular, cria sua conta com nome, e-mail e senha e, a partir
daí, entra com e-mail e senha para usar o sistema. Sem estar autenticada, ela só vê as telas de
cadastro e login.

**Why this priority**: tudo no sistema (grupos, sorteio, sigilo) depende de saber quem é o
usuário. Sem identidade, não há como garantir que cada um veja apenas o próprio resultado.

**Independent Test**: criar uma conta, sair, entrar novamente e confirmar que a tela inicial
autenticada é exibida; tentar acessar uma tela interna sem login e confirmar o redirecionamento
para o login.

**Acceptance Scenarios**:

1. **Given** um visitante sem conta, **When** ele informa nome, e-mail válido e senha válida e
   confirma o cadastro, **Then** a conta é criada e ele entra autenticado na tela inicial.
2. **Given** um e-mail já cadastrado, **When** alguém tenta criar outra conta com esse e-mail,
   **Then** o cadastro é recusado com mensagem clara.
3. **Given** um usuário cadastrado, **When** ele informa e-mail e senha corretos, **Then** ele
   entra no sistema e vê seus grupos.
4. **Given** um usuário cadastrado, **When** ele informa senha incorreta, **Then** o acesso é
   negado com mensagem genérica que não revela se o e-mail existe.
5. **Given** um usuário autenticado, **When** ele escolhe "Sair", **Then** a sessão é encerrada
   e telas internas deixam de ser acessíveis sem novo login.

---

### User Story 2 - Criar grupo e reunir participantes pelo código (Priority: P1)

Um usuário autenticado cria um grupo de amigo secreto dando um nome a ele. O sistema gera um
código único, que o organizador compartilha (por exemplo, no WhatsApp). Os outros participantes
entram no sistema, informam o código e passam a fazer parte do grupo. Todos os membros veem a
lista de quem já entrou, atualizada à medida que novas pessoas chegam.

**Why this priority**: o sorteio só existe se houver um grupo com participantes; é o segundo
passo indispensável da jornada.

**Independent Test**: um usuário cria um grupo e recebe o código; outros dois usuários entram com
esse código; os três veem a mesma lista de participantes.

**Acceptance Scenarios**:

1. **Given** um usuário autenticado, **When** ele cria um grupo informando um nome, **Then** o
   grupo é criado, ele se torna organizador e participante, e um código único é exibido com
   opção de copiar e compartilhar.
2. **Given** um código de grupo válido de um grupo ainda não sorteado, **When** outro usuário
   autenticado informa o código, **Then** ele passa a ser participante e vê o grupo.
3. **Given** um código inexistente ou digitado errado, **When** o usuário tenta entrar, **Then**
   recebe a mensagem "código inválido" e nada é alterado.
4. **Given** um usuário que já participa do grupo, **When** ele informa o mesmo código de novo,
   **Then** é levado ao grupo, sem ser duplicado na lista.
5. **Given** membros com a tela do grupo aberta, **When** um novo participante entra, **Then** a
   lista de participantes é atualizada para todos sem que precisem recarregar a página.
6. **Given** um usuário que participa de vários grupos, **When** ele abre a tela inicial,
   **Then** vê todos os seus grupos com nome e situação (aguardando participantes / sorteado).

---

### User Story 3 - Realizar o sorteio e ver o próprio amigo secreto (Priority: P1)

Quando todos os participantes já entraram, o organizador inicia o sorteio. O sistema sorteia
todos de uma só vez: cada participante tira exatamente uma pessoa, ninguém tira a si mesmo e
cada pessoa é tirada por exatamente um participante. Assim que o sorteio termina, todos os
participantes passam a poder ver — cada um apenas o seu — quem tiraram. Ninguém, nem o
organizador, consegue ver o que os outros tiraram.

**Why this priority**: é o propósito do sistema; sem ele, nada entrega valor.

**Independent Test**: com um grupo de 3+ participantes, o organizador inicia o sorteio; cada
participante entra e vê um único nome, diferente do próprio; os nomes vistos formam uma troca
completa (todos dão e recebem um presente); nenhum participante consegue descobrir o par de
outro.

**Acceptance Scenarios**:

1. **Given** um grupo com menos de 3 participantes, **When** o organizador tenta iniciar o
   sorteio, **Then** o sistema impede e informa que são necessários pelo menos 3 participantes.
2. **Given** um grupo apto ao sorteio, **When** um participante que não é o organizador abre o
   grupo, **Then** ele não tem a opção de iniciar o sorteio.
3. **Given** um grupo apto, **When** o organizador inicia o sorteio e confirma, **Then** todos os
   participantes são sorteados de uma só vez, ninguém tira a si mesmo e cada participante dá e
   recebe exatamente um presente.
4. **Given** que uma tentativa interna de sorteio resultaria em alguém tirando a si mesmo,
   **When** o sorteio é processado, **Then** ele é refeito automaticamente antes de qualquer
   resultado ser salvo ou exibido, sem que os usuários percebam.
5. **Given** participantes com a tela do grupo aberta, **When** o sorteio é concluído, **Then**
   todos são avisados ao mesmo tempo, sem recarregar a página, de que o sorteio aconteceu.
6. **Given** um grupo sorteado, **When** um participante abre o grupo, **Then** o nome do seu
   amigo secreto fica oculto até ele tocar em "Revelar", e então só ele vê esse nome.
7. **Given** um grupo sorteado, **When** qualquer participante (inclusive o organizador) procura
   saber quem outro participante tirou, **Then** o sistema não oferece nenhuma forma de obter
   essa informação.
8. **Given** um grupo sorteado, **When** um novo usuário tenta entrar com o código, **Then** a
   entrada é recusada com a mensagem de que o sorteio já foi realizado.
9. **Given** o organizador clica duas vezes ou duas requisições de sorteio chegam juntas,
   **When** o sistema processa, **Then** apenas um sorteio é realizado.

---

### User Story 4 - Ajustar participantes antes do sorteio (Priority: P3)

Antes do sorteio, um participante que entrou por engano pode sair do grupo, e o organizador pode
remover alguém que não deveria estar lá.

**Why this priority**: corrige erros comuns de entrada, mas o fluxo principal funciona sem isso.

**Independent Test**: com um grupo não sorteado, um participante sai e outro é removido pelo
organizador; ambos deixam de aparecer na lista e perdem acesso ao grupo.

**Acceptance Scenarios**:

1. **Given** um grupo não sorteado, **When** um participante (que não é o organizador) escolhe
   sair, **Then** ele deixa de fazer parte do grupo e some da lista de todos.
2. **Given** um grupo não sorteado, **When** o organizador remove um participante, **Then** esse
   participante sai da lista e perde acesso ao grupo.
3. **Given** um grupo já sorteado, **When** alguém tenta sair ou remover um participante,
   **Then** o sistema impede, pois isso desfaria o sorteio.

---

### Edge Cases

- **Exatamente 3 participantes**: o sorteio funciona; cada um tira um dos outros dois.
- **Grupo cheio**: ao atingir o limite de participantes (ver Assumptions), novas entradas são
  recusadas com mensagem clara.
- **Código com letras minúsculas ou espaços**: o código é aceito sem diferenciar maiúsculas e
  minúsculas e ignorando espaços nas pontas.
- **Tentativas repetidas de adivinhar códigos**: após várias tentativas com códigos inválidos em
  pouco tempo, novas tentativas do mesmo usuário são temporariamente bloqueadas.
- **Tentativas repetidas de login**: após várias senhas erradas em sequência, novas tentativas
  para aquele acesso são temporariamente bloqueadas.
- **Usuário sem acesso ao grupo**: quem não é participante e tenta abrir um grupo diretamente
  (por link) não vê nome, participantes nem resultado; recebe "grupo não encontrado".
- **Falha durante o sorteio**: se algo der errado no meio do processo, nenhum resultado parcial
  fica salvo; o grupo permanece "aguardando sorteio" e o organizador pode tentar de novo.
- **Participante sem conexão no momento do sorteio**: ao abrir o sistema depois, vê o grupo como
  sorteado e pode revelar seu resultado normalmente.
- **Organizador tenta sair do próprio grupo**: não é permitido; o organizador é sempre
  participante do grupo que criou.
- **Sessão expirada no meio do uso**: o usuário é levado ao login e, após entrar, volta a ver
  seus grupos sem perda de dados.

## Requirements *(mandatory)*

### Functional Requirements

**Conta e acesso**

- **FR-001**: O sistema MUST permitir o cadastro com nome, e-mail e senha; o e-mail MUST ser
  único no sistema.
- **FR-002**: O sistema MUST exigir senha com no mínimo 8 caracteres.
- **FR-003**: O sistema MUST permitir login com e-mail e senha; em caso de falha, a mensagem MUST
  ser genérica, sem indicar se o e-mail existe.
- **FR-004**: O sistema MUST bloquear temporariamente o login após tentativas consecutivas
  malsucedidas em curto intervalo.
- **FR-005**: O sistema MUST permitir que o usuário saia (logout) e MUST encerrar sessões
  inativas após um período de inatividade.
- **FR-006**: Nesta versão o sistema NÃO oferece recuperação de senha ("esqueci minha senha");
  a tela de login MUST NOT exibir essa opção.
- **FR-007**: Todas as telas e funções, exceto cadastro e login, MUST exigir usuário autenticado.

**Grupos**

- **FR-008**: Um usuário autenticado MUST poder criar um grupo informando um nome (obrigatório,
  até 60 caracteres); o criador se torna organizador e participante desse grupo.
- **FR-009**: Cada grupo MUST receber um código único, fácil de digitar no celular (curto, sem
  caracteres ambíguos como 0/O e 1/I) e impossível de adivinhar na prática.
- **FR-010**: O sistema MUST exibir o código ao organizador com opção de copiar e de compartilhar.
- **FR-011**: Um usuário autenticado MUST poder entrar em um grupo informando o código, sem
  diferenciar maiúsculas de minúsculas.
- **FR-012**: O sistema MUST impedir que um mesmo usuário seja incluído duas vezes no mesmo grupo.
- **FR-013**: O sistema MUST limitar tentativas de entrada com códigos inválidos por usuário em
  curto intervalo.
- **FR-014**: Participantes MUST ver o nome do grupo, quem é o organizador, a lista de nomes dos
  participantes, a quantidade de participantes e a situação do grupo.
- **FR-015**: A lista de participantes MUST ser atualizada para quem está com o grupo aberto,
  sem recarregar a página, quando alguém entra, sai ou é removido.
- **FR-016**: O usuário MUST ver, na tela inicial, todos os grupos de que participa, com nome e
  situação.
- **FR-017**: Usuários que não participam de um grupo MUST NOT ver nenhuma informação desse grupo.
- **FR-018**: Antes do sorteio, um participante (exceto o organizador) MUST poder sair do grupo, e
  o organizador MUST poder remover participantes.
- **FR-019**: Após o sorteio, o sistema MUST impedir novas entradas, saídas e remoções.

**Sorteio**

- **FR-020**: Somente o organizador MUST poder iniciar o sorteio, e apenas com no mínimo 3
  participantes. Não há número esperado de participantes: o organizador acompanha a lista e
  decide, manualmente, quando todos entraram e o sorteio pode começar.
- **FR-021**: O sistema MUST pedir confirmação ao organizador antes de sortear, avisando que,
  após o sorteio, ninguém mais poderá entrar ou sair do grupo.
- **FR-022**: O sorteio MUST ser realizado de uma só vez para todo o grupo: cada participante
  tira exatamente uma pessoa e cada pessoa é tirada por exatamente um participante.
- **FR-023**: Nenhum participante MUST tirar a si mesmo; se uma tentativa interna gerar esse
  caso, o sorteio MUST ser refeito automaticamente antes de qualquer resultado ser salvo ou
  exibido.
- **FR-024**: O resultado MUST ser imprevisível e não pode ser influenciado por nenhum
  participante, inclusive o organizador.
- **FR-025**: O sorteio MUST ocorrer uma única vez por grupo, mesmo com cliques repetidos ou
  pedidos simultâneos. O resultado é definitivo: depois de sorteado, ninguém (nem o
  organizador) pode cancelar ou refazer o sorteio.
- **FR-026**: Se o sorteio falhar, nenhum resultado parcial MUST ser mantido e o grupo MUST
  voltar a "aguardando sorteio".
- **FR-027**: Ao concluir o sorteio, a situação do grupo MUST mudar para "sorteado" para todos os
  participantes ao mesmo tempo; quem estiver com o grupo aberto MUST ser avisado sem recarregar
  a página.

**Sigilo**

- **FR-028**: Cada participante MUST ver somente o nome do seu próprio amigo secreto.
- **FR-029**: Nenhum participante, incluindo o organizador, MUST conseguir descobrir por qualquer
  tela ou função quem outro participante tirou.
- **FR-030**: O sistema MUST NOT oferecer função de "revelar todos os pares", nem mesmo ao
  organizador.
- **FR-031**: O nome do amigo secreto MUST ficar oculto na tela até o próprio participante tocar
  em "Revelar", evitando que alguém ao lado veja por acaso; ele pode ocultar novamente.
- **FR-032**: O resultado de um participante MUST NOT aparecer em mensagens de erro, avisos,
  notificações ou registros de atividade acessíveis a outras pessoas.

**Experiência**

- **FR-033**: Todas as telas MUST funcionar em celulares a partir de 360px de largura, sem
  rolagem horizontal, e também em tablet e computador.
- **FR-034**: A interface MUST estar em português do Brasil.
- **FR-035**: Toda ação MUST mostrar claramente quando está carregando, quando deu certo e quando
  deu errado, com mensagem compreensível.

### Key Entities

- **Usuário**: pessoa com conta no sistema. Atributos: nome de exibição, e-mail (único), senha
  (guardada de forma protegida), data de cadastro. Pode participar de vários grupos.
- **Grupo**: um amigo secreto específico. Atributos: nome, código único, organizador, situação
  (aguardando sorteio / sorteado), data de criação, data do sorteio. Tem de 1 a N participantes.
- **Participação**: vínculo entre um usuário e um grupo. Atributos: data de entrada, se é o
  organizador. Um usuário tem no máximo uma participação por grupo.
- **Resultado do sorteio (Par)**: para cada participante de um grupo sorteado, quem ele tirou.
  Existe um par por participante; ninguém é par de si mesmo; cada participante aparece
  exatamente uma vez como "quem tira" e uma vez como "quem é tirado". Informação sigilosa:
  visível apenas a quem tirou.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um novo usuário consegue criar a conta e criar um grupo, já com o código para
  compartilhar, em menos de 2 minutos pelo celular.
- **SC-002**: Um participante que recebeu o código consegue entrar no grupo em menos de 30
  segundos após o login.
- **SC-003**: Em 10.000 sorteios simulados com grupos de 3 a 100 participantes, 100% dos
  resultados não têm ninguém tirando a si mesmo e todos os participantes dão e recebem
  exatamente um presente.
- **SC-004**: Após o organizador confirmar o sorteio, todos os participantes com o grupo aberto
  veem a situação "sorteado" em até 5 segundos, sem recarregar a página.
- **SC-005**: Nos testes de segurança, 0 tentativas de um participante (inclusive o organizador)
  de obter o amigo secreto de outro participante têm sucesso.
- **SC-006**: Todas as telas são utilizáveis em celulares de 360px de largura sem rolagem
  horizontal e com botões fáceis de tocar.
- **SC-007**: Pelo menos 90% dos participantes de um teste com usuários conseguem entrar no grupo
  e revelar seu amigo secreto na primeira tentativa, sem ajuda.
- **SC-008**: O sistema atende um grupo de 100 participantes acessando ao mesmo tempo no momento
  do sorteio sem erros e com cada tela respondendo em até 2 segundos.

## Assumptions

- O organizador participa do sorteio como qualquer outro participante.
- Um usuário pode participar de vários grupos e criar vários grupos.
- Tamanho do grupo: mínimo de 3 participantes para sortear (exigência da constituição, para
  preservar o sigilo) e máximo de 100 participantes por grupo.
- O nome exibido nos grupos e no resultado é o nome informado no cadastro.
- O refazimento do sorteio quando alguém tira a si mesmo é interno e transparente: os usuários
  só veem o resultado final válido.
- Sessões inativas expiram após um período padrão de mercado (ex.: algumas horas), definido no
  plano técnico.
- Os participantes compartilham o código fora do sistema (WhatsApp, e-mail etc.); o sistema não
  envia convites.
- Os dados dos grupos ficam disponíveis enquanto o sistema existir; exclusão de conta e de grupo
  não fazem parte desta versão.
- **Fora do escopo desta versão**: lista de desejos/sugestões de presente, valor do presente,
  data do evento, chat, envio de e-mails ou notificações push, recuperação de senha, cancelar ou
  refazer um sorteio concluído, número esperado de participantes, login com redes sociais,
  restrições de sorteio (ex.: "casais não se tiram"), excluir conta e excluir grupo.
