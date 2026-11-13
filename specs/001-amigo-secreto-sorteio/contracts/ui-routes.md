# Contrato: telas e rotas do frontend

SPA React com layout mobile-first (360px+). Rotas marcadas como 🔒 exigem sessão. Sem sessão,
o usuário é levado para `/entrar?voltar=<rota>` e, depois do login, volta à rota pedida
(Edge Case "sessão expirada"). Usuário logado que abre `/entrar` ou `/cadastro` vai para `/`.

| Rota | Tela | Requisitos | Conteúdo principal |
|------|------|-----------|--------------------|
| `/entrar` | Login | FR-003, FR-004, FR-006 | e-mail, senha, "Entrar", link "Criar conta". **Sem** "esqueci minha senha" |
| `/cadastro` | Cadastro | FR-001, FR-002 | nome, e-mail, senha (com regra visível), "Criar conta" |
| `/` 🔒 | Meus grupos | FR-016 | cartões dos grupos (nome, nº de participantes, selo "Aguardando sorteio"/"Sorteado", selo "Organizador"); botões "Criar grupo" e "Entrar com código"; estado vazio amigável |
| `/grupos/novo` 🔒 | Criar grupo | FR-008 | nome do grupo → vai para `/grupos/:id` |
| `/entrar-no-grupo` 🔒 | Entrar com código | FR-011, FR-013 | campo de código com máscara `XXXX-XXXX`, maiúsculas automáticas, teclado sem autocorreção. Aceita `?codigo=` para links compartilhados |
| `/grupos/:id` 🔒 | Grupo | FR-010, FR-014, FR-015, FR-018–FR-021, FR-027–FR-031 | ver abaixo |

## Tela `/grupos/:id`

**Status `OPEN`**

- Nome do grupo e selo "Aguardando sorteio".
- Código em destaque, com os botões **Copiar** e **Compartilhar** (Web Share API quando
  disponível, com fallback para copiar). O link compartilhado é `/entrar-no-grupo?codigo=XXXX-XXXX`.
- Lista de participantes (nome + selo "Organizador"), contador `N participantes` e atualização
  ao vivo via SSE.
- **Organizador**:
  - Botão "Remover" em cada participante (exceto ele mesmo), com confirmação.
  - Botão "Realizar sorteio", desabilitado com a dica "São necessários pelo menos 3
    participantes" enquanto houver menos de 3.
  - Antes de sortear, um modal de confirmação avisa que ninguém mais poderá entrar ou sair
    (FR-021).
- **Participante**: botão "Sair do grupo", com confirmação.

**Status `DRAWN`**

- Selo "Sorteado" com a data.
- Cartão "Seu amigo secreto" com o nome **oculto** (ex.: `••••••`) e o botão **Revelar**.
  Tocar busca `GET /my-secret-friend` e mostra o nome. O botão **Ocultar** esconde de novo
  (FR-031). O nome não fica em cache persistente nem em `localStorage`.
- Lista de participantes (somente nomes), sem nenhuma indicação de quem tirou quem.
- Sem botões de entrar, sair, remover ou sortear de novo.

**Transição ao vivo**: quem está na tela no momento do sorteio recebe `group-drawn`, e a tela
passa para o estado `DRAWN` com o aviso "O sorteio foi realizado! Toque em Revelar para ver quem
você tirou." (SC-004).

## Padrões de interface (Constituição VI)

- Tailwind 4 com tokens de tema (`@theme`) para cor primária, superfícies, raios e sombras;
  tema claro e escuro conforme o sistema.
- Alvos de toque ≥ 44×44 px, contraste WCAG 2.1 AA, foco visível e `label` em todo campo.
- Estados de carregamento (skeleton/spinner nos botões), sucesso (toast) e erro (mensagem do
  `detail` do Problem) em toda ação (FR-035).
- Textos em pt-BR (FR-034).
