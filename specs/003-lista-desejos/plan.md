# Implementation Plan: Lista de Desejos

**Branch**: `003-lista-desejos` | **Date**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-lista-desejos/spec.md`

## Summary

Cada participante monta, em cada grupo, uma lista de até 5 sugestões de presente (descrição e
link opcional) e a edita enquanto o grupo não for finalizado. Depois do sorteio, quem tirou a
pessoa vê a lista dela junto do nome revelado — e mais ninguém, nem o organizador. O grupo ganha
um valor do presente opcional, informado pelo organizador e fixo depois do sorteio.

Abordagem técnica: evolução do monólito das features 001/002, **sem dependências novas**. Uma
migração Flyway (V7): tabela `wish_item` ligada à participação com `ON DELETE CASCADE`, coluna
`wishlist_updated_at` na participação e `gift_value` no grupo. A lista de quem o usuário tirou
vem pela rota que já revela o nome (`my-secret-friend`), resolvida a partir do par do usuário da
sessão — nenhuma rota recebe id de usuário para ler lista. As edições passam pelo mesmo
`GroupLock` das demais escritas e não publicam sinais SSE. Decisões em
[research.md](./research.md) (R26–R32).

## Technical Context

**Language/Version**: Java 25 LTS (backend); TypeScript 6.0 (frontend) — sem mudança

**Primary Dependencies**: as das features 001/002 (Spring Boot 4.1.2, Spring Security 7, Data
JPA, Flyway, Spring Session JDBC, Bucket4j; React 19.3, Vite 8.3, Tailwind 4.3, React Router,
TanStack Query 5, react-hook-form + zod 4). **Nenhuma dependência nova** e nenhuma atualização
de versão (política de dependências do projeto)

**Storage**: PostgreSQL 18.7 — `secret_group` e `group_member` ganham uma coluna cada; nova
`wish_item` ([data-model.md](./data-model.md)); migração `V7`

**Testing**: JUnit 5, Mockito, AssertJ, spring-security-test, Testcontainers; Vitest, React
Testing Library, MSW — estratégia em research R32

**Target Platform**: containers Linux (Docker Compose, Docker no WSL); navegadores móveis e
desktop

**Project Type**: aplicação web (backend REST + SPA) — a mesma das features anteriores

**Performance Goals**: revelar nome + lista com a mesma latência de hoje (uma consulta a mais,
por índice, de no máximo 5 linhas); editar um item em < 300 ms no servidor

**Constraints**: sigilo (Constituição I) — a lista de outra pessoa só pelo caminho do resultado
do sorteio; nenhum sinal SSE dependente de quem tirou quem; links nunca acessados pelo servidor;
funcionar a partir de 360px; mudanças de API apenas aditivas em `/api/v1`

**Scale/Scope**: até 100 participantes × 5 itens por grupo; 1 instância do backend; nenhuma
tela nova (2 telas alteradas); 3 rotas REST novas + 2 alteradas; nenhum evento SSE novo
([contracts/](./contracts/))

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constituição **v1.2.1**.

| # | Princípio | Como o plano cumpre | Pré-pesquisa | Pós-design |
|---|-----------|---------------------|:---:|:---:|
| I | Sigilo (não negociável) | A lista de outra pessoa só sai em `my-secret-friend`, a partir do par do usuário da sessão, sem o id de quem foi tirado (R27); rotas de edição só em `mine`, 404 para item alheio (R28); nenhum sinal SSE de lista (sse-events.md); nada registra quem viu a lista (FR-015); `draw_assignment` sem nenhum método novo; testes de sigilo ampliados (R32) | ✅ | ✅ |
| II | Sorteio justo e simultâneo (não negociável) | Sorteio intocado; a lista não participa do sorteio; sorteio desfeito continua apagando os pares, e a lista some junto do nome porque vem na mesma resposta (R27) | ✅ | ✅ |
| III | Segurança por padrão | Identidade sempre da sessão; Bean Validation nos corpos novos; CSRF nas escritas; links só `http`/`https`, nunca buscados pelo servidor (sem SSRF), abertos com `noopener noreferrer` (R29); texto do usuário nunca como HTML (FR-008) | ✅ | ✅ |
| IV | Testes obrigatórios (não negociável) | Teste primeiro nas regras (limite, link, valor, `wishlistUpdatedAt`), na autorização e no sigilo; Testcontainers para a migração e o fluxo; gate de 80% mantido (R32) | ✅ | ✅ |
| V | Varredura contínua | Sem mudança: CodeQL, Trivy, `npm audit` e Gitleaks no PR | ✅ | ✅ |
| VI | Mobile-first e moderno | Cartões e formulários nos padrões existentes; modal de site externo; estados de carregamento/sucesso/erro; pt-BR ([ui-routes.md](./contracts/ui-routes.md)) | ✅ | ✅ |
| VII | Containerização | Sem mudança nos containers; migração na subida | ✅ | ✅ |
| VIII | Simplicidade | Nenhuma dependência nova; a lista reaproveita a rota do resultado em vez de uma nova; sem tabela de "lista" só para uma data (R26); sem SSE novo | ✅ | ✅ |
| — | Stack obrigatória e `ddl-auto: validate` | Sem mudança | ✅ | ✅ |
| — | Datas em UTC | `created_at` e `wishlist_updated_at` em `timestamptz` UTC; "Atualizada em" exibido em horário de Brasília | ✅ | ✅ |

**Resultado**: nenhum desvio. *Complexity Tracking* não se aplica.

## Project Structure

### Documentation (this feature)

```text
specs/003-lista-desejos/
├── plan.md              # Este arquivo
├── research.md          # Fase 0: decisões R26–R32
├── data-model.md        # Fase 1: colunas e tabela novas, quem lê o quê, migração
├── quickstart.md        # Fase 1: migração, roteiro manual, sigilo
├── contracts/
│   ├── openapi.yaml     # só rotas e schemas novos/alterados (união com 001 e 002)
│   ├── sse-events.md    # nenhum evento novo (e por quê)
│   └── ui-routes.md     # mudanças nas telas
├── checklists/
│   └── requirements.md
└── tasks.md             # Fase 2 (/speckit-tasks — ainda não criado)
```

### Source Code (repository root)

Só o que esta feature cria (➕) ou altera (✏️):

```text
backend/src/main/
├── java/br/com/amigosecreto/
│   ├── wishlist/                           ➕ pacote novo (R26, R28)
│   │   ├── WishItem.java                   # entidade; descrição, url, createdAt
│   │   ├── WishItemRepository.java         # itens do dono na ordem; contagem
│   │   ├── WishItemUrl.java                # validação do link (R29)
│   │   ├── WishlistService.java            # adicionar, alterar, remover (GroupLock, limite 5)
│   │   ├── WishlistController.java         # /groups/{id}/wishlist/mine/items
│   │   └── dto/                            # WishItemRequest, WishItemResponse, MyWishlist, FriendWishItem
│   ├── group/
│   │   ├── SecretGroup.java                ✏️ giftValue; regra de alteração (só OPEN)
│   │   ├── GroupMember.java                ✏️ wishlistUpdatedAt, touchWishlist()
│   │   ├── GroupService.java               ✏️ criar/editar com giftValue; GroupDetail.myWishlist
│   │   └── dto/                            ✏️ EventDetailsRequest/Response (giftValue), GroupDetail (myWishlist)
│   └── draw/
│       ├── SecretFriendService.java        ✏️ lista de quem o usuário tirou (R27)
│       └── dto/SecretFriendResponse.java   ✏️ wishlist, wishlistUpdatedAt
└── resources/db/migration/
    └── V7__wishlist_and_gift_value.sql     ➕

backend/src/test/java/br/com/amigosecreto/
├── wishlist/ group/ draw/                  ➕✏️ unitários e de serviço
├── web/                                    ➕✏️ @WebMvcTest das rotas novas e alteradas
└── integration/                            ➕ MigrationV7IT, WishlistFlowIT
                                            ✏️ SecrecyIT (listas), OpenApiContractIT (contratos 001 + 002 + 003)

frontend/src/
├── api/groups.ts                           ✏️ giftValue, myWishlist, SecretFriend.wishlist
├── api/wishlist.ts                         ➕ adicionar, alterar, remover
├── lib/schemas.ts                          ✏️ zod do item e do valor do presente
├── components/group/
│   ├── WishlistEditor.tsx                  ➕ a própria lista
│   ├── WishlistView.tsx                    ➕ a lista de quem o usuário tirou
│   ├── ExternalLinkModal.tsx               ➕ aviso de site externo (FR-017)
│   ├── SecretFriendCard.tsx                ✏️ mostra valor e lista junto do nome
│   ├── EventDetailsCard.tsx                ✏️ valor do presente
│   └── EventDetailsForm.tsx                ✏️ campo do valor (desabilitado após o sorteio)
└── pages/
    ├── CreateGroupPage.tsx                 ✏️ campo do valor
    └── GroupPage.tsx                       ✏️ cartão "Minha lista de desejos"
```

**Structure Decision**: mesma estrutura das features anteriores (monorepo `backend/` +
`frontend/`, backend por feature com controller → service → repository). A lista ganha um pacote
próprio (`wishlist`) porque tem entidade, regras e rotas próprias. A leitura da lista de quem o
usuário tirou fica no `SecretFriendService` (pacote `draw`), o único que sabe resolver o par —
ele só chama o repositório de itens com o id já resolvido. O valor do presente é um detalhe do
evento e fica em `SecretGroup`.

## Riscos e mitigação

| Risco | Mitigação |
|-------|-----------|
| Uma rota ou resposta revelar a lista de outra pessoa (e com ela o par) | Leitura só em `my-secret-friend`; nenhuma rota com id de usuário; `GroupDetail` só com `myWishlist`; `SecrecyIT` com 3 listas diferentes e o organizador (SC-002) |
| Item de outra pessoa alterado por id (IDOR) | Busca sempre por `(id, groupId, userId da sessão)`; 404 igual para todos os casos; teste web e de integração |
| Sinal ou aviso que dependa de quem tirou quem | Nenhum SSE novo; nenhum registro de visualização; "Atualizada em" só calculado na leitura de quem já pode ver (R27) |
| Link malicioso (`javascript:`, phishing, rastreamento) | Só `http`/`https` absolutos; servidor nunca busca; modal de site externo; `noopener noreferrer` (R29) |
| Lista órfã depois de sair do grupo | `ON DELETE CASCADE` na FK para `group_member`; teste de integração e consulta do quickstart §3 |
| Valor do presente mudar depois do sorteio | regra em `SecretGroup` com teste; campo desabilitado na tela; `409 gift-value-locked` |
| Contrato OpenAPI em três arquivos se desencontrar | `OpenApiContractIT` compara a união 001 + 002 + 003 com a API gerada |

## Complexity Tracking

Não se aplica: o Constitution Check não teve violações.
