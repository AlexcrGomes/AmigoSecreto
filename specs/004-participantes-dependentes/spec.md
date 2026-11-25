# Feature Specification: Participantes Dependentes

**Feature Branch**: `004-participantes-dependentes`

**Created**: 2026-11-25

**Status**: Draft

**Input**: User description: "Participantes dependentes. Para ter conta é necessário ter e-mail, mas nem todos têm — por exemplo, um pai com um filho criança. A criança não precisa ter um usuário: o pai (um participante com conta) adiciona um novo participante ao grupo indicando que é seu dependente. O dependente participa do sorteio como qualquer outro participante, e o responsável (o pai) é quem vê, depois do sorteio, quem o dependente tirou."

**Depende de**: [001](../001-amigo-secreto-sorteio/spec.md) (contas, grupos, sorteio sigiloso),
[002](../002-grupo-evento-confirmacao/spec.md) (confirmação, finalizado, saída depois do sorteio)
e [003](../003-lista-desejos/spec.md) (lista de desejos). Tudo o que lá está continua valendo,
salvo o que esta spec acrescenta.

**Glossário** (complementa o das features anteriores):

- **Dependente**: pessoa sem conta no sistema (por exemplo, uma criança) que participa de um
  grupo como qualquer participante, mas não entra no sistema: tudo o que é dela passa pelo
  responsável.
- **Responsável**: participante com conta que adicionou o dependente ao grupo e age por ele.
- **Participante com conta**: o participante das features anteriores, que entra no sistema com
  e-mail e senha.

**Governança (Constituição, Princípio I)**: a constituição v1.2.1 diz que cada usuário MUST ver
**somente** o amigo secreto que ele próprio tirou. Nesta feature o responsável passa a ver também
o resultado do dependente — o que exige uma **emenda à constituição antes do plano**, como a
feature 002 fez para desfazer o sorteio. A emenda precisa dizer, no mínimo: o responsável vê o
resultado dos próprios dependentes, e de mais ninguém; um dependente não tem acesso próprio; e
nenhuma outra pessoa (nem o organizador) passa a ver resultados de dependentes de terceiros. A
emenda também precisa tratar do **Princípio II**: o sorteio passa a ter uma restrição além de
"ninguém tira a si mesmo" — responsável e dependente nunca se tiram (Clarifications).

## Clarifications

### Session 2026-11-25

- Q: O dependente pode tirar o próprio responsável (e o responsável tirar o dependente)? → A: Não:
  responsável e dependente nunca se tiram, em nenhum dos dois sentidos.
- Q: O dependente é cadastrado só no grupo ou fica salvo na conta do responsável? → A: Só no grupo;
  em cada grupo o responsável adiciona o dependente de novo, e nada fica guardado fora do grupo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Adicionar um dependente ao grupo (Priority: P1)

O participante abre o grupo e adiciona um dependente informando só o nome (por exemplo, "Lucas").
O dependente aparece na lista de participantes como "Lucas (dependente de Ana)" e conta como
participante para o sorteio, para o limite do grupo e para as confirmações. Antes do sorteio, o
responsável pode mudar o nome do dependente ou removê-lo do grupo.

**Why this priority**: sem poder adicionar o dependente, quem não tem e-mail fica de fora — é o
problema que a feature resolve.

**Independent Test**: Ana entra num grupo, adiciona "Lucas" como dependente; todos os participantes
veem "Lucas (dependente de Ana)" na lista; a contagem de participantes sobe; Ana renomeia e depois
remove o Lucas, e ele some da lista.

**Acceptance Scenarios**:

1. **Given** um participante de um grupo aguardando sorteio, **When** ele adiciona um dependente
   com um nome válido, **Then** o dependente passa a participar do grupo, aparece na lista com a
   indicação de quem é o responsável e todos que estão com o grupo aberto veem a mudança.
2. **Given** um nome vazio ou com menos de 2 caracteres, **When** o participante tenta adicionar,
   **Then** o sistema impede e explica o que corrigir.
3. **Given** um grupo cheio (100 participantes, contando os dependentes), **When** alguém tenta
   adicionar um dependente, **Then** o sistema impede, como faz com quem entra pelo código.
4. **Given** um dependente, **When** o responsável muda o nome dele ou o remove antes do sorteio,
   **Then** a lista mostra o nome novo ou deixa de mostrar o dependente.
5. **Given** um dependente, **When** outro participante (que não é o responsável) procura alterar
   ou remover esse dependente, **Then** não consegue — exceto o organizador, que pode removê-lo
   antes do sorteio, como faz com qualquer participante.
6. **Given** um grupo sorteado ou finalizado, **When** alguém tenta adicionar um dependente,
   **Then** o sistema impede, como faz com quem tenta entrar depois do sorteio.

---

### User Story 2 - O responsável vê quem o dependente tirou (Priority: P1)

Depois do sorteio, o responsável vê, além do próprio amigo secreto, um cartão para cada dependente:
"Amigo secreto do Lucas", com o mesmo "Revelar"/"Ocultar". Ninguém mais vê o resultado do
dependente — nem o organizador, nem quem foi tirado por ele.

**Why this priority**: é o motivo de o dependente participar; sem isso, ninguém saberia para quem o
Lucas vai dar o presente.

**Independent Test**: num grupo sorteado com Ana (responsável do Lucas), Bruno e Carla, Ana revela o
próprio amigo e o do Lucas; Bruno e Carla revelam só o próprio; nenhuma outra tela, resposta ou
aviso mostra o resultado do Lucas para quem não é a Ana.

**Acceptance Scenarios**:

1. **Given** um grupo sorteado, **When** o responsável abre o grupo, **Then** vê o cartão do
   próprio amigo secreto e um cartão para cada dependente, todos começando ocultos.
2. **Given** o cartão de um dependente, **When** o responsável toca em "Revelar", **Then** vê o
   nome de quem o dependente tirou e a lista de desejos dessa pessoa (feature 003); "Ocultar"
   esconde os dois.
3. **Given** um participante que não é o responsável (inclusive o organizador), **When** ele procura
   o resultado do dependente, **Then** não existe forma de vê-lo.
4. **Given** um participante que tirou o dependente, **When** revela o próprio amigo secreto,
   **Then** vê o nome do dependente e a lista de desejos dele, como com qualquer participante.
5. **Given** um grupo finalizado, **When** o responsável revela o amigo do dependente, **Then**
   continua vendo, como na feature 002.

---

### User Story 3 - O responsável age pelo dependente (Priority: P2)

O responsável cuida de tudo o que o dependente faria se tivesse conta: a confirmação de
participação (feature 002), a lista de desejos (feature 003) e o pedido de saída depois do sorteio
(feature 002).

**Why this priority**: completa o ciclo do dependente nas regras já existentes; sem isso o
dependente travaria o sorteio (pendente) ou ficaria sem lista.

**Independent Test**: Ana adiciona o Lucas; ao confirmar a própria participação, o Lucas fica
confirmado junto; Ana monta a lista do Lucas; depois do sorteio, quem tirou o Lucas vê a lista
dele; Ana pede a saída do Lucas e, com a aprovação do organizador, o sorteio é desfeito.

**Acceptance Scenarios**:

1. **Given** um responsável que adiciona um dependente, **When** ele confirma a própria
   participação, **Then** os dependentes dele ficam confirmados junto; se o responsável já estava
   confirmado, o dependente entra confirmado.
2. **Given** que o organizador muda data, horário ou local antes do sorteio (feature 002), **When**
   as confirmações voltam a pendente, **Then** os dependentes também voltam e são confirmados de novo
   junto com o responsável.
3. **Given** um dependente, **When** o responsável abre o grupo, **Then** pode montar a lista de
   desejos do dependente, com as mesmas regras da própria (até 5 itens, só antes de o grupo ser
   finalizado).
4. **Given** um grupo sorteado, **When** o responsável pede a saída do dependente e o organizador
   aprova, **Then** o dependente sai, o sorteio é desfeito e o dependente não pode ser adicionado de
   novo àquele grupo, como na feature 002.
5. **Given** um responsável que sai ou é removido do grupo antes do sorteio, **When** isso acontece,
   **Then** os dependentes dele saem junto.

---

### Edge Cases

- **Responsável com vários dependentes**: cada dependente tem cartão, confirmação e lista
  próprios; o responsável vê os resultados de todos os seus dependentes e de mais ninguém.
- **Dois responsáveis querem o mesmo dependente** (pai e mãe no mesmo grupo): cada dependente tem
  um único responsável naquele grupo; o outro não vê o resultado do dependente.
- **Nome repetido**: dois dependentes podem ter nomes iguais aos de outros participantes; a lista
  mostra o responsável ao lado para diferenciar.
- **O organizador como responsável**: o organizador pode ter dependentes, com as mesmas regras.
- **Saída do responsável depois do sorteio**: quando o organizador aprova a saída do responsável,
  os dependentes dele saem junto, numa única saída que desfaz o sorteio.
- **Responsável perde o acesso à conta**: o dependente continua no grupo; ninguém mais assume o
  resultado dele (fora do escopo transferir a responsabilidade).
- **Grupo pequeno**: num grupo de 3 participantes em que um é dependente, o responsável conhece dois
  dos três pares; como já acontece em qualquer grupo de 3 (feature 001), dá para deduzir o resto.
- **Quem tirou o dependente**: vê o nome do dependente como o de qualquer participante; não fica
  sabendo quem é o responsável por isso — a lista de participantes já mostra.
- **Responsável e dependente se tirando**: nunca acontece, em nenhum dos dois sentidos — assim o
  responsável não fica sabendo quem o tirou e o resultado do dependente não é ele mesmo.
- **Dependentes do mesmo responsável** (irmãos): podem se tirar; o responsável conhece os dois
  resultados, mas os dependentes não usam o sistema, então ninguém perde a surpresa por isso.
- **Sorteio impossível**: com as restrições, um grupo pode não ter sorteio válido (ex.: Ana, o
  dependente Lucas e o Bruno — Ana e Lucas só poderiam tirar o Bruno). O sorteio é recusado com um
  motivo claro, e o organizador vê o motivo antes de tentar.
- **Mesmo dependente em vários grupos**: o responsável adiciona o dependente em cada grupo; para o
  sistema, são participações independentes, sem ligação entre os grupos.

## Requirements *(mandatory)*

### Functional Requirements

**Dependentes no grupo**

- **FR-001**: Um participante com conta MUST poder adicionar dependentes a um grupo de que participa,
  enquanto o grupo estiver aguardando sorteio, informando só o nome (2 a 80 caracteres).
- **FR-002**: O dependente MUST NOT ter conta, e-mail ou senha, nem entrar no sistema; tudo o que é
  dele é feito pelo responsável.
- **FR-003**: Cada responsável MUST poder ter até 5 dependentes por grupo.
- **FR-004**: O dependente MUST contar como participante: no limite de 100 por grupo, no mínimo de 3
  para o sorteio, na lista e na contagem de participantes.
- **FR-005**: A lista de participantes MUST mostrar o dependente com a indicação do responsável
  (ex.: "Lucas (dependente de Ana)").
- **FR-006**: Antes do sorteio, o responsável MUST poder renomear e remover os próprios dependentes;
  o organizador MUST poder remover qualquer dependente, como remove participantes (feature 001).
- **FR-007**: Ninguém além do responsável MUST poder renomear um dependente ou agir em nome dele.
- **FR-008**: O dependente MUST existir só dentro do grupo em que foi adicionado; para participar de
  outro grupo, o responsável o adiciona de novo, e nada do dependente fica guardado fora do grupo.

**Sorteio e resultado**

- **FR-009**: O dependente MUST participar do sorteio exatamente como um participante com conta: tira
  alguém, é tirado por alguém, nunca tira a si mesmo.
- **FR-009a**: O sorteio MUST NOT fazer um responsável tirar um dependente seu, nem um dependente tirar
  o próprio responsável.
- **FR-009b**: Quando as restrições tornam o sorteio impossível, o sistema MUST recusá-lo com um motivo
  claro ("Com os dependentes deste grupo, não há sorteio possível. Convide mais participantes."), e o
  organizador MUST ver esse motivo antes de tentar sortear, como os demais bloqueios (feature 002).
- **FR-010**: Depois do sorteio, o responsável MUST poder revelar, separadamente, o próprio amigo
  secreto e o de cada dependente seu, com as mesmas regras de sigilo do resultado (Revelar/Ocultar,
  nada guardado no aparelho).
- **FR-011**: O resultado de um dependente MUST ser visível somente para o responsável; nenhuma outra
  pessoa (nem o organizador) MUST conseguir vê-lo, por nenhuma tela, resposta, aviso em tempo real,
  mensagem de erro ou registro.
- **FR-012**: O sistema MUST decidir quais resultados mostrar só a partir do usuário da sessão e dos
  dependentes de que ele é responsável, nunca de um identificador enviado pela tela.

**O responsável age pelo dependente**

- **FR-013**: A confirmação do responsável MUST valer também para os dependentes dele; o dependente
  MUST ficar pendente quando o responsável fica pendente (feature 002).
- **FR-014**: O responsável MUST poder montar a lista de desejos de cada dependente, com as regras da
  feature 003; quem tirar o dependente MUST ver essa lista ao revelar.
- **FR-015**: Depois do sorteio, o responsável MUST poder pedir a saída de um dependente, com as regras
  e a aprovação do organizador da feature 002; quem saiu MUST NOT poder ser adicionado de novo ao
  mesmo grupo.
- **FR-016**: Quando o responsável deixa o grupo (sai, é removido ou tem a saída aprovada), os
  dependentes dele MUST deixar o grupo junto, na mesma operação.

### Key Entities

- **Dependente**: nome; responsável (um participante com conta); grupo; confirmação, lista de
  desejos e resultado do sorteio próprios. Sem e-mail, senha ou sessão.
- **Participante** (alterado): passa a ser um participante com conta ou um dependente; o sorteio, a
  confirmação e a lista de desejos valem para os dois.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um participante adiciona um dependente ao grupo em menos de 30 segundos pelo celular.
- **SC-002**: Nos testes de segurança, 0 tentativas de ver o resultado de um dependente têm sucesso
  para quem não é o responsável — inclusive o organizador e quem foi tirado pelo dependente.
- **SC-003**: Nos testes, 100% dos sorteios com dependentes seguem as mesmas regras de qualquer
  sorteio (todos tiram e são tirados, ninguém tira a si mesmo) e nenhum responsável e dependente se
  tiram; grupos sem sorteio possível são sempre recusados com o motivo.
- **SC-004**: Nos testes, 100% das tentativas de agir por um dependente sem ser o responsável são
  recusadas.
- **SC-005**: Pelo menos 90% dos responsáveis de um teste com usuários encontram, sem ajuda, o amigo
  secreto do dependente logo depois do sorteio.

## Assumptions

- Dependentes são, em geral, crianças ou pessoas sem e-mail; a idade não é registrada nem
  verificada.
- O responsável é quem conhece o dependente e já age por ele fora do sistema; o sistema não pede
  prova dessa relação.
- O mínimo de 3 participantes para o sorteio continua igual, contando os dependentes; com
  dependentes, o sorteio também pode ser recusado por não haver sorteio possível (FR-009b).
- Os avisos ao vivo e as regras de confirmação, finalização e saída das features anteriores valem
  para os dependentes, sempre por meio do responsável.
- **Fora do escopo desta versão**: o dependente ter acesso próprio (link, PIN), transformar um
  dependente em conta com e-mail, transferir o dependente para outro responsável, dois responsáveis
  para o mesmo dependente, registrar idade ou parentesco.
