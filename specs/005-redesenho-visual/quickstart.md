# Quickstart: validar a feature 005 (redesenho visual)

Pré-requisitos, `.env`, subida e testes são **os mesmos** das features anteriores (Docker no WSL,
`docker compose up -d --build`, `npm run test:coverage` no container do Node). Componentes em
[contracts/ui-components.md](./contracts/ui-components.md), telas em
[contracts/ui-routes.md](./contracts/ui-routes.md), tokens em [data-model.md](./data-model.md).

> O cadastro tem limite de tentativas por IP: crie as contas do roteiro uma vez e reaproveite-as
> com login.

> **Revisão de 2026-11-27**: tema único claro (branco, vermelho e dourado) e dois desenhos, celular
> (< 768px) e tablet/iPad/computador (≥ 768px). O roteiro abaixo já é o da revisão.

## 1. Build e segurança

```bash
docker compose up -d --build
docker compose exec frontend sh -c "grep -c 'glisse-styles' /usr/share/nginx/html/index.html"
```

**Esperado**: `1` (o marcador do research R44). No navegador, o console **não** mostra violação de
CSP (`Refused to apply inline style`), e os botões e a barra de abas aparecem com o visual de vidro.

## 2. Roteiro visual

Use o modo de dispositivo do navegador nas larguras **360px**, **390px** (celulares), **768px** (iPad
em pé), **1024px** (iPad deitado) e **1280px** (computador). "Celular" = 360/390; "largo" = 768 em
diante.

| # | Passo | Esperado | Cobre |
|---|-------|----------|-------|
| 1 | Abrir `/entrar` e `/cadastro`; enviar vazio | celular: marca acima e formulário num cartão; largo: duas metades (marca vermelha com dourado / formulário); rótulos visíveis; erros com ícone e texto; sem navegação | US3-1, US4-5, FR-011 |
| 2 | Pôr o sistema operacional em modo escuro com a tela aberta | nada muda: continua branco com vermelho e dourado | US1-1, US1-3, FR-004 |
| 3 | Entrar; ver "Meus grupos" vazio e com grupos | celular: lista, botões lado a lado no topo, barra de abas embaixo com "Meus grupos" marcada; largo: grade de 2 ou 3 colunas, botões ao lado do título, menu lateral com "Meus grupos" em vermelho | US3-2, US4-4, FR-015 |
| 4 | Criar grupo e entrar com código pela navegação | campos em pílula, data/hora claros; no largo, cartão centralizado; item certo marcado | US3-3, US3-4, US4-6 |
| 5 | Percorrer todas as telas procurando verde | nenhum verde; sucesso e "Confirmado"/"Sorteado" em dourado, com texto | US1-2, FR-016 |
| 6 | Rolar até o fim de uma tela comprida a 360px | o último botão fica inteiro acima da barra | US3-6 |
| 7 | Focar um campo a 360px | a barra de abas some enquanto o teclado está aberto | Edge Case |
| 8 | Tocar em "Sair" (barra ou menu lateral) e cancelar; depois confirmar | "Sair da sua conta?"; cancelar fica; confirmar vai para `/entrar` | US3-5 |
| 9 | Grupo aguardando sorteio (organizador, com dependentes) a 360px | cabeçalho do grupo e abas Resumo · Pessoas · Desejos; Resumo com detalhes, código e sortear; Pessoas com participantes e dependentes; Desejos com as listas | US4-1 |
| 10 | Trocar de aba, voltar no navegador e recarregar | a aba fica no endereço (`?aba=`); recarregar mantém; voltar sai do grupo | US4-2 |
| 11 | O mesmo grupo a 1024px e 1280px | sem abas; resumo à esquerda, pessoas e desejos à direita | US4-3 |
| 12 | Começar a editar um item da lista e girar o tablet (768 ↔ 1024) / redimensionar cruzando 768px | a edição e o texto digitado continuam; a navegação muda de forma sem sair da tela | US4-7, Edge Case |
| 13 | Sortear e revelar "Seu amigo secreto" e o de um dependente; trocar de aba e voltar | transição suave; o nome continua revelado; com o nome oculto, o DOM não tem o nome (aba Elementos) | US2-2, FR-014 |
| 14 | Executar ações com erro (ex.: confirmar sem rede) | carregamento, erro com ícone e texto, sucesso — tudo no visual novo | US2-3, FR-012 |
| 15 | Abrir uma janela (remover, sortear) e navegar com Tab | vidro sobre a tela; foco preso; `Esc` fecha e o foco volta | US2-4, FR-010 |
| 16 | Grupo sorteado e finalizado, nas duas larguras | selos de status, cartões e avisos no visual novo | US2 |
| 17 | Ativar "reduzir transparência" | superfícies sólidas, mesmas cores e cantos | US1-4, FR-008 |
| 18 | Ativar "reduzir movimento" | sem transições nem lentes animadas | FR-008 |
| 19 | Abrir no Safari (iPhone ou macOS) | vidro fosco ou refração do fundo; nada quebrado; nada escuro | US1-5, FR-009 |

## 3. Verificações automáticas e de medida

1. **Contraste (SC-002)**: `npm run test:coverage` inclui `contrast.test.ts` (todos os pares de
   tokens no tema único, sem bloco escuro nem verde). Conferir também com o painel de
   acessibilidade do navegador em 3 telas.
2. **Sem rolagem horizontal (SC-003)**: em cada tela e em cada largura, no console:
   `document.documentElement.scrollWidth === document.documentElement.clientWidth` → `true`.
3. **Desempenho (SC-004)**: Lighthouse móvel (4G lenta simulada) em `/` e `/grupos/:id`:
   primeira tela utilizável (TTI) ≤ 3 s; no painel de desempenho, rolar a tela do grupo sem quadros
   longos repetidos.
4. **Sigilo (FR-014)**: com o nome oculto, `document.body.innerText` não contém o nome; nada em
   `localStorage`/`sessionStorage`.
