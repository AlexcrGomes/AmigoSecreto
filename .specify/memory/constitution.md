# Amigo Secreto Constitution

## Core Principles

### I. Sigilo do Amigo Secreto (NÃO NEGOCIÁVEL)

O resultado do sorteio é informação confidencial de cada participante.

- Cada usuário MUST conseguir ver **somente** o amigo secreto que ele próprio tirou.
- Endpoints, telas, logs, mensagens de erro, eventos e respostas de API MUST NOT expor o par
  (quem tirou quem) de outro usuário — isso inclui o criador/dono do grupo.
- MUST NOT existir endpoint administrativo, de depuração ou "listar todos os pares".
- A API MUST derivar a identidade exclusivamente do usuário autenticado; IDs de usuário vindos
  do cliente NUNCA são usados para decidir qual resultado retornar (prevenção de IDOR).
- Os pares MUST ser armazenados apenas na medida necessária e nunca registrados em logs; a
  proteção em repouso (ex.: criptografia de coluna) MUST ser avaliada e decidida no `plan`.
- Testes automatizados MUST comprovar que um usuário não consegue obter o resultado de outro.

**Justificativa**: o valor do amigo secreto está na surpresa; um vazamento destrói o propósito
do sistema.

### II. Sorteio Justo, Válido e Simultâneo (NÃO NEGOCIÁVEL)

- Cada sorteio MUST ser executado **uma única vez, no servidor**, para o grupo inteiro, em uma
  transação atômica — nunca no cliente e nunca de forma parcial ou por usuário.
- O resultado MUST ser um *derangement*: cada participante dá e recebe exatamente um presente
  e **ninguém tira a si mesmo**. Se uma tentativa produzir auto-sorteio, o sorteio MUST ser
  refeito até ser válido, antes de qualquer resultado ser persistido ou exibido.
- A aleatoriedade MUST usar fonte criptograficamente segura (`SecureRandom`); o resultado não
  pode ser previsível nem influenciável por qualquer participante.
- O sorteio MUST ser idempotente: um grupo sorteado não pode ser sorteado novamente por
  requisições concorrentes ou repetidas (controle de estado + bloqueio/versão no banco).
- O resultado MUST ficar disponível para todos os participantes do grupo no mesmo momento
  (mudança de estado única do grupo); o mecanismo de notificação (polling, SSE ou WebSocket)
  é decidido no `plan`.
- O sorteio MUST exigir no mínimo 3 participantes (com 2, cada um deduziria o par do outro,
  violando o Princípio I).
- Um sorteio concluído é **definitivo**, com uma única exceção: o organizador aprovar o pedido
  de saída de um participante que não seja o organizador. Nesse caso, em uma única transação,
  o participante sai do grupo, **todos os pares do sorteio são descartados de forma definitiva
  e irrecuperável** (inclusive para quem administra o sistema) e o grupo volta a "aguardando
  sorteio". Um novo sorteio entre quem permaneceu segue todas as regras deste princípio e é
  independente do anterior.
- O descarte vale para o banco de dados do sistema: os pares MUST ser apagados na mesma
  transação, e a aplicação MUST NOT guardar cópia, log ou histórico do sorteio desfeito.
  Backups do banco são cópias externas à aplicação e MUST ter a retenção mínima necessária e
  ficar guardados separados da chave de criptografia dos pares, para que ninguém, nem quem
  administra, combine um backup antigo com a chave para recuperar um sorteio desfeito.
- Ninguém, nem o organizador, pode desfazer ou refazer um sorteio por outro caminho. Quem saiu
  de um grupo depois do sorteio MUST NOT poder entrar de novo nesse grupo, para impedir que
  saídas combinadas sejam usadas para sortear até obter um resultado desejado.

**Justificativa**: estas são as regras de negócio centrais; qualquer violação torna o sistema
incorreto. A exceção da saída aprovada evita recriar o grupo quando alguém desiste, sem abrir
caminho para que um sorteio seja refeito à vontade.

### III. Segurança por Padrão

- Todas as rotas MUST exigir autenticação, exceto cadastro, login, obtenção do token CSRF e verificação de saúde. Rotas públicas MUST
  NOT expor dados de usuários ou grupos.
- Senhas MUST ser armazenadas apenas com hash forte e salt (BCrypt ou Argon2); nunca em texto
  plano nem com criptografia reversível.
- Tokens/sessões MUST ter expiração; se forem usados cookies, MUST ser `HttpOnly`, `Secure` e
  `SameSite`. Tokens MUST NOT ser guardados em `localStorage` sem justificativa no `plan`.
- Autorização MUST ser verificada no servidor a cada requisição: só membros de um grupo acessam
  dados desse grupo; só o criador pode iniciar o sorteio e aprovar pedidos de saída.
- Códigos de grupo MUST ser únicos, não sequenciais e gerados com `SecureRandom` com entropia
  suficiente para impedir adivinhação; login e entrada por código MUST ter limitação de
  tentativas (rate limiting).
- Toda entrada MUST ser validada no backend (Bean Validation); SQL apenas parametrizado (JPA).
- Segredos (senhas de banco, chaves JWT) MUST vir de variáveis de ambiente/secrets; NUNCA
  versionados. O repositório MUST conter apenas `.env.example`.
- A aplicação MUST seguir o OWASP Top 10, com CORS restritivo e cabeçalhos de segurança
  (CSP, X-Content-Type-Options, X-Frame-Options/frame-ancestors, HSTS em produção).

**Justificativa**: o sistema tem login e dados pessoais; segurança não é opcional.

### IV. Testes Automatizados Obrigatórios (NÃO NEGOCIÁVEL)

- Toda regra de negócio MUST ter testes unitários: backend com JUnit 5 + Mockito + AssertJ;
  frontend com Vitest + React Testing Library.
- As regras críticas (algoritmo do sorteio, sigilo, autorização, geração de código) MUST ser
  escritas com testes primeiro (Red → Green → Refactor).
- O algoritmo do sorteio MUST ter testes de propriedade/repetição (milhares de execuções, vários
  tamanhos de grupo) verificando: nenhum auto-sorteio, cada participante dá e recebe exatamente
  uma vez.
- Integração com o banco MUST ser testada com PostgreSQL real via Testcontainers (sem H2).
- Cobertura mínima: **80% de linhas** nas camadas de domínio/serviço do backend e na lógica do
  frontend; o CI MUST falhar abaixo disso (JaCoCo / Vitest coverage).
- Um bug corrigido MUST vir acompanhado de um teste que o reproduz.

**Justificativa**: testes são a prova objetiva de que os Princípios I e II são cumpridos.

### V. Varredura de Segurança Contínua

Toda Pull Request e todo push na `main` MUST executar, no GitHub Actions:

- **SAST** (análise estática de código) — ex.: CodeQL para Java e TypeScript.
- **SCA** (dependências vulneráveis) — ex.: OWASP Dependency-Check / `npm audit`.
- **Varredura de imagens Docker** — ex.: Trivy.
- **Detecção de segredos** — ex.: Gitleaks.

Achados de severidade **Crítica ou Alta** MUST bloquear o merge, salvo exceção documentada no
PR com justificativa e prazo de correção. As ferramentas exatas são fixadas no `plan`.

**Justificativa**: vulnerabilidades devem ser encontradas antes de chegar à `main`, não depois.

### VI. Mobile-First e Interface Moderna

- A interface MUST ser projetada primeiro para celular (largura mínima de 360px) e se adaptar
  a tablet e desktop, sem rolagem horizontal.
- O estilo MUST usar Tailwind CSS com um conjunto consistente de cores, espaçamentos e
  componentes; CSS ad hoc só quando o Tailwind não atender.
- Áreas de toque MUST ter no mínimo 44×44px; contraste de cores MUST atender WCAG 2.1 AA;
  formulários MUST ter rótulos e mensagens de erro claras.
- Toda ação assíncrona MUST mostrar estados de carregamento, sucesso e erro.
- O idioma da interface é **português do Brasil**.

**Justificativa**: os participantes usarão principalmente o celular, muitas vezes em grupo e
ao mesmo tempo.

### VII. Containerização e Paridade de Ambientes

- Todo o sistema (frontend, backend, PostgreSQL) MUST subir com um único `docker compose up`.
- Imagens MUST usar build multi-stage, versões de imagem base fixadas (sem `latest`), execução
  como usuário não-root e `healthcheck`.
- Configuração MUST vir de variáveis de ambiente (12-Factor); a mesma imagem serve a todos os
  ambientes.
- Rodar o sistema MUST exigir apenas Docker e Git na máquina; o build Java/Node acontece
  dentro dos containers.

**Justificativa**: elimina o "funciona na minha máquina" e torna o deploy previsível.

### VIII. Simplicidade

- Arquitetura: monorepo com `backend/` (Spring Boot, monólito em camadas: controller →
  service → repository) e `frontend/` (React SPA). Microsserviços são proibidos sem emenda a
  esta constituição.
- YAGNI: só se implementa o que a spec pede; dependências novas MUST ser justificadas no `plan`.
- Contratos de API REST MUST ser documentados (OpenAPI) e versionados por prefixo (`/api/v1`).

**Justificativa**: o problema é pequeno; complexidade extra só aumenta a superfície de bugs e
de ataque.

## Stack Tecnológica e Restrições

| Camada         | Tecnologia obrigatória                                                    |
|----------------|---------------------------------------------------------------------------|
| Backend        | Java (LTS vigente) + Spring Boot (linha estável com suporte), Maven       |
| Segurança      | Spring Security                                                           |
| Persistência   | PostgreSQL + Spring Data JPA; migrações versionadas com Flyway            |
| Frontend       | React + Vite + TypeScript                                                 |
| Estilo         | Tailwind CSS                                                              |
| Testes         | JUnit 5, Mockito, AssertJ, Testcontainers, Vitest, React Testing Library  |
| Containers     | Docker + Docker Compose                                                   |
| Repositório/CI | GitHub + GitHub Actions                                                   |

- As versões exatas de cada item MUST ser fixadas no `plan` da primeira feature e só mudam via
  PR dedicado.
- O schema do banco MUST ser alterado apenas por migrações Flyway; `ddl-auto` MUST ficar
  `validate` (nunca `update`/`create`) fora dos testes.
- Datas/horas MUST ser armazenadas em UTC.
- O JDK local da máquina de desenvolvimento não é requisito: o build oficial acontece em
  container (Princípio VII).

## Fluxo de Desenvolvimento e Quality Gates

**Ciclo SDD (Spec Kit)** — toda feature segue, em ordem:
`/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` →
(`/speckit-analyze`) → `/speckit-implement`. Não se escreve código de produção sem spec, plan e
tasks aprovados.

**Git e GitHub**:

- Cada feature em sua branch `NNN-nome-da-feature`; integração na `main` apenas via Pull
  Request. A `main` MUST ser protegida: sem push direto (inclusive para administradores, sem
  bypass), e merge só com todos os gates abaixo verdes.
- Commits seguem Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`…).

**Quality gates obrigatórios no CI para merge**:

1. Build do backend e do frontend.
2. Testes unitários e de integração passando.
3. Cobertura ≥ 80% (Princípio IV).
4. Lint/format sem erros (ex.: Spotless/Checkstyle no Java, ESLint + Prettier no frontend).
5. Varreduras de segurança sem achados Críticos/Altos (Princípio V).
6. Build das imagens Docker com sucesso.
7. Aprovação manual de um mantenedor, verificando conformidade com esta constituição
   (checklist dos Princípios I a VIII no PR). Com um único mantenedor, a aprovação é feita no
   ambiente protegido `aprovacao-manual` do GitHub (o mantenedor como revisor obrigatório),
   cujo job é check obrigatório e só roda depois dos gates 1 a 6. Todo novo commit no PR
   invalida a aprovação anterior. Com dois ou mais mantenedores, passa a valer também a
   aprovação de PR por alguém que não seja o autor.

## Governance

- Esta constituição prevalece sobre qualquer outra prática, convenção ou preferência do
  projeto. Em caso de conflito, ela vence.
- **Emendas**: feitas por PR que altera `.specify/memory/constitution.md` via
  `/speckit-constitution`, com justificativa, Sync Impact Report e plano de migração quando
  afetar código existente.
- **Versionamento** (SemVer):
  - MAJOR — remoção ou redefinição incompatível de princípio;
  - MINOR — princípio/seção nova ou orientação materialmente ampliada;
  - PATCH — esclarecimentos e correções de redação.
- **Conformidade**: todo `plan` MUST passar pelo "Constitution Check" antes da pesquisa e
  novamente após o design; toda violação MUST ser registrada na tabela de complexidade do
  `plan` com justificativa. Toda aprovação de PR MUST verificar os Princípios I a VIII.
- Princípios marcados como NÃO NEGOCIÁVEL (I, II, IV) não admitem exceção, apenas emenda.

**Version**: 1.2.1 | **Ratified**: 2026-11-12 | **Last Amended**: 2026-11-18
