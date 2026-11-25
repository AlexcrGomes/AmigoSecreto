# Research: Lista de Desejos

Decisões técnicas da feature 003. A numeração continua a das features anteriores
(001: R1–R12; 002: R13–R25). Nenhum item do Technical Context ficou como *NEEDS CLARIFICATION*:
a stack, o banco, os testes e a entrega são os mesmos, e a política do projeto proíbe
dependências novas.

---

## R26. Onde guardar a lista e os itens

**Decision**: tabela nova `wish_item` (um item por linha) e uma coluna nova
`group_member.wishlist_updated_at`.

- `wish_item (id, group_id, user_id, description, url, created_at)`, com FK composta
  `(group_id, user_id) → group_member (group_id, user_id) ON DELETE CASCADE`: quando a
  participação acaba (saiu, removido, saída aprovada), os itens somem junto (FR-018), sem código
  extra nos três caminhos de saída.
- A "lista" não precisa de tabela própria: é o conjunto de itens de um membro, e a participação
  (`group_member`) já é a dona natural. `wishlist_updated_at` fica nela e muda em toda inclusão,
  edição ou remoção de item (inclusive quando a lista fica vazia), para o "Atualizada em dd/mm"
  (FR-015a).
- Ordem de cadastro (FR-007) por `created_at` e, no empate, `id`.
- Limite de 5 itens (FR-001) verificado no serviço, com o grupo travado (R28), e não por trigger.

**Rationale**: o `ON DELETE CASCADE` amarra o ciclo de vida da lista ao da participação no
próprio banco. Uma coluna na participação evita uma tabela de "lista" só para guardar uma data.

**Alternatives considered**:
- Lista como JSON numa coluna de `group_member` — mais simples de gravar, mas perde validação por
  coluna, tamanho máximo e edição item a item (Edge Case das duas abas).
- Cifrar os itens como os pares (R9) — os itens não dizem quem tirou quem; o que precisa de sigilo
  é o *caminho* até a lista de outra pessoa (R27). Cifrar só acrescentaria custo.

---

## R27. Ler a lista de quem tirei: pelo mesmo caminho do nome (FR-009 a FR-011)

**Decision**: a lista de quem o usuário tirou vem **na mesma resposta** de
`GET /api/v1/groups/{groupId}/my-secret-friend`, que passa de `{name}` para
`{name, wishlist, wishlistUpdatedAt}`. O `SecretFriendService` já descobre quem o usuário da
sessão tirou (decifrando o par dele); com esse id, busca os itens por `(groupId, receiverId)`.

- O id de quem foi tirado **nunca** sai na resposta; o cliente não tem como pedir a lista de
  outra pessoa — não existe rota que receba um id de usuário para ler lista (FR-010).
- `wishlistUpdatedAt` só vem preenchido se a lista mudou **depois** do sorteio
  (`wishlist_updated_at > drawn_at`); senão, `null` e a tela não mostra a indicação (FR-015a).
- Mesma regra de situação da rota hoje: `DRAWN` ou `FINISHED`; em `OPEN` (inclusive depois de um
  sorteio desfeito), `409 group-not-drawn` — quem tirava a pessoa perde a lista junto com o nome
  (FR-013).
- A resposta continua com `Cache-Control: no-store`; no cliente, a lista vive na mesma consulta
  do nome (TanStack Query com `gcTime: 0`) e some junto em "Ocultar" (FR-011).

**Rationale**: o sigilo já está resolvido e testado nesse caminho (Constituição I). Uma rota
separada "lista do meu amigo secreto" duplicaria a resolução do par e criaria uma segunda
superfície para testar; uma rota "lista do usuário X" seria um IDOR por construção.

**Alternatives considered**:
- `GET /groups/{id}/wishlist/of-my-secret-friend` — mesma lógica, mais uma rota e mais uma
  chamada na tela; nenhum ganho.
- Mandar a lista do amigo secreto dentro de `GroupDetail` — o detalhe do grupo é recarregado por
  todo sinal SSE e por todos; misturar o resultado do sorteio nele aumenta o risco de vazamento.

---

## R28. Editar a própria lista

**Decision**: três rotas, sempre sobre a lista do **usuário da sessão** (o caminho diz `mine`):

| Rota | Faz |
|------|-----|
| `POST /api/v1/groups/{groupId}/wishlist/mine/items` | adiciona um item (`201`) |
| `PUT /api/v1/groups/{groupId}/wishlist/mine/items/{itemId}` | altera descrição e link |
| `DELETE /api/v1/groups/{groupId}/wishlist/mine/items/{itemId}` | remove (`204`) |

- As três devolvem a lista atualizada do próprio usuário (exceto o `DELETE`, `204`), e a própria
  lista também vem no `GroupDetail` (`myWishlist`) — que já é por usuário —, então não há rota
  `GET` separada.
- Item de outra pessoa, de outro grupo ou inexistente: `404` igual (sem dizer qual caso).
- Todas travam o grupo pelo `GroupLock` (finaliza se o evento passou) e recusam grupo finalizado
  com `409 group-finished` (FR-005). Não membro: `404`.
- Limite: o 6º item → `409 wishlist-full`, "A lista tem no máximo 5 sugestões."
- Nenhuma delas publica sinal SSE: alterar a lista não muda nada que os outros vejam, e um aviso
  para quem tirou o dono é proibido (FR-015).

**Rationale**: item a item atende o Edge Case das duas abas (cada item tem a sua última gravação)
e mantém as mensagens de erro por campo simples. Trancar o grupo é barato e mantém a regra
transversal da 002 (toda escrita passa pelo `GroupLock`).

**Alternatives considered**:
- `PUT /wishlist/mine` com a lista inteira — uma rota só, mas uma aba sobrescreveria os itens
  que a outra acabou de criar.

---

## R29. Validação do link (FR-003, FR-017)

**Decision**: no servidor, o link é aceito só se `java.net.URI` o interpreta como absoluto, com
esquema `http` ou `https`, com host, e com até 500 caracteres. O sistema **nunca** acessa o link
(sem pré-visualização). No cliente, o link é exibido como texto do endereço e aberto com
`target="_blank" rel="noopener noreferrer nofollow"`, depois de um aviso "Você vai abrir um site
externo".

**Rationale**: `noreferrer` impede que o site saiba de qual página o usuário veio (FR-017) e
`noopener` impede que ele controle a aba do sistema. Não buscar o link evita SSRF. Esquemas como
`javascript:` e `data:` ficam de fora.

**Alternatives considered**: lista de domínios permitidos (lojas conhecidas) — limitaria sem
motivo e exigiria manutenção.

---

## R30. Valor do presente no grupo (FR-020 a FR-023)

**Decision**: coluna `secret_group.gift_value integer NULL`, com
`CHECK (gift_value BETWEEN 1 AND 100000)`. Entra como campo opcional `giftValue` em
`EventDetailsRequest` (criar e `PUT event-details`) e sai em `EventDetailsResponse`.

- Pode ser informado, alterado ou removido enquanto o grupo está `OPEN`.
- Com o grupo `DRAWN`, o `PUT event-details` aceita o **mesmo** valor (o formulário reenvia todos
  os campos) e recusa um valor diferente com `409 gift-value-locked`, "O valor do presente não
  muda depois do sorteio." Os demais detalhes continuam editáveis, como na 002.
- Mudar o valor **não** sobe `detailsVersion` nem volta confirmações a pendente (FR-023), mas
  publica o `group-updated` que o `PUT` já publica, para todos verem o valor novo.
- Sorteio desfeito → grupo `OPEN` → valor editável de novo (Edge Case).

**Rationale**: o valor é um detalhe do evento e vive no mesmo formulário; uma rota própria seria
mais uma escrita para travar e testar.

**Alternatives considered**: guardar o valor em centavos — a spec fixou reais inteiros.

---

## R31. Interface

**Decision**:

- `components/group/WishlistEditor.tsx` (novo): a própria lista na tela do grupo — itens com
  "Editar" e "Remover" (com confirmação), "Adicionar sugestão" até 5, contador "N de 5";
  somente leitura em `FINISHED`. Formulário com react-hook-form + zod, como os demais.
- `components/group/WishlistView.tsx` (novo): a lista de quem o usuário tirou, dentro do
  `SecretFriendCard`, abaixo do nome; "Atualizada em dd/mm" quando houver; "Ainda não há
  sugestões de presente" quando vazia; link com o aviso de site externo.
- `EventDetailsCard` mostra "Valor do presente: R$ 80"; `EventDetailsForm` ganha o campo
  (desabilitado, com explicação, depois do sorteio); o cartão "Seu amigo secreto" repete o valor
  junto da lista.
- Texto do usuário sempre como texto do React, nunca HTML (FR-008).

**Rationale**: reaproveita os padrões da 001/002 (cartões, `ConfirmModal`, toasts, zod), sem
dependência nova.

---

## R32. Estratégia de testes

**Decision** (além do que a 001/002 já testam):

- **Unidade**: validação do link e da descrição; limite de 5; `wishlist_updated_at` em incluir,
  editar e remover; regra do valor (`OPEN` livre, `DRAWN` só o mesmo valor); `wishlistUpdatedAt`
  só depois do sorteio.
- **Web (`@WebMvcTest`)**: as três rotas exigem sessão e CSRF; 404 para item alheio; 409 para
  grupo finalizado e lista cheia.
- **Integração (Testcontainers)**: `WishlistFlowIT` com os cenários das US1–US3; **sigilo**
  (`SecrecyIT` ampliado): com 3 participantes e listas diferentes, cada um recebe só a lista de
  quem tirou; o organizador não vê a de ninguém; nenhuma outra resposta, evento SSE ou log contém
  itens de lista; depois de um sorteio desfeito, quem tirava a pessoa recebe `409` e não a lista;
  `ON DELETE CASCADE` apaga a lista de quem sai; `OpenApiContractIT` passa a ler os contratos
  001 + 002 + 003.
- **Frontend**: `WishlistEditor`, `WishlistView` (vazio, atualizada, link externo), formulário do
  valor (desabilitado após o sorteio) e `SecretFriendCard` (lista some com "Ocultar").

**Rationale**: o risco principal é sigilo (Constituição I, IV); os testes de integração provam
que não há caminho até a lista de outra pessoa.
