# Quickstart: validar a feature 005 (redesenho visual)

Pré-requisitos, `.env`, subida e testes são **os mesmos** das features anteriores (Docker no WSL,
`docker compose up -d --build`, `npm run test:coverage` no container do Node). Componentes em
[contracts/ui-components.md](./contracts/ui-components.md), telas em
[contracts/ui-routes.md](./contracts/ui-routes.md), tokens em [data-model.md](./data-model.md).

> O cadastro tem limite de tentativas por IP: crie as contas do roteiro uma vez e reaproveite-as
> com login.

## 1. Build e segurança

```bash
docker compose up -d --build
docker compose exec frontend sh -c "grep -c 'glisse-styles' /usr/share/nginx/html/index.html"
```

**Esperado**: `1` (o marcador do research R44). No navegador, o console **não** mostra violação de
CSP (`Refused to apply inline style`), e os botões e a barra de abas aparecem com o visual de vidro.

## 2. Roteiro visual

Use duas janelas: uma a **360px** (celular) e outra a **1280px** (computador). Repita cada passo
com o sistema operacional em **modo claro** e em **modo escuro**.

| # | Passo | Esperado | Cobre |
|---|-------|----------|-------|
| 1 | Abrir `/entrar` e `/cadastro`; enviar vazio | fundo festivo, formulário em vidro, rótulos visíveis, erros com ícone e texto; sem abas | US3-1, FR-011 |
| 2 | Trocar o modo do aparelho com a tela aberta | o tema muda na hora, sem recarregar | US1-3, FR-004 |
| 3 | Entrar; ver "Meus grupos" vazio e com grupos | cartões em vidro, seções separadas; abas embaixo (360px) ou no topo (1280px), "Meus grupos" marcada | US3-2, FR-015 |
| 4 | Criar grupo e entrar com código pelas abas | campos em pílula, data/hora nativos no mesmo estilo; aba certa marcada | US3-3, US3-4 |
| 5 | Rolar até o fim de uma tela comprida (grupo com muitos participantes) a 360px | o último botão fica inteiro acima da barra | US3-6 |
| 6 | Focar um campo a 360px | a barra de abas some enquanto o teclado está aberto | Edge Case |
| 7 | Tocar em "Sair" e cancelar; depois confirmar | "Sair da sua conta?"; cancelar fica; confirmar vai para `/entrar` | US3-5 |
| 8 | Grupo aguardando sorteio (organizador, com dependentes) | todos os cartões no visual novo, mesma ordem e ações | US2-1 |
| 9 | Sortear e revelar "Seu amigo secreto" e o de um dependente | transição suave; com o nome oculto, o DOM não tem o nome (aba Elementos) | US2-2, FR-014 |
| 10 | Executar ações com erro (ex.: confirmar sem rede) | carregamento, erro com ícone e texto, sucesso — tudo no visual novo | US2-3, FR-012 |
| 11 | Abrir uma janela (remover, sortear) e navegar com Tab | vidro sobre a tela; foco preso; `Esc` fecha e o foco volta | US2-4, FR-010 |
| 12 | Grupo sorteado e finalizado | selos de status, cartões e avisos no visual novo | US2 |
| 13 | Ativar "reduzir transparência" (macOS/iOS: Acessibilidade → Tela) | superfícies sólidas, mesmas cores e cantos | US1-4, FR-008 |
| 14 | Ativar "reduzir movimento" | sem transições nem lentes animadas | FR-008 |
| 15 | Abrir no Safari (iPhone ou macOS) | vidro fosco ou dobrando o fundo festivo; nada quebrado | US1-5, FR-009 |
| 16 | Girar o tablet / redimensionar a janela cruzando 768px | a navegação muda de lugar sem sair da tela | Edge Case |

## 3. Verificações automáticas e de medida

1. **Contraste (SC-002)**: `npm run test:coverage` inclui `contrast.test.ts` (todos os pares de
   tokens, dois temas). Conferir também com o painel de acessibilidade do navegador em 3 telas.
2. **360px sem rolagem (SC-003)**: em cada tela, no console:
   `document.documentElement.scrollWidth === document.documentElement.clientWidth` → `true`.
3. **Desempenho (SC-004)**: Lighthouse móvel (4G lenta simulada) em `/` e `/grupos/:id`:
   primeira tela utilizável (TTI) ≤ 3 s; no painel de desempenho, rolar a tela do grupo sem quadros
   longos repetidos.
4. **Sigilo (FR-014)**: com o nome oculto, `document.body.innerText` não contém o nome; nada em
   `localStorage`/`sessionStorage`.
