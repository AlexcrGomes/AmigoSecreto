## Resumo

<!-- O que muda e por quê. Cite a feature/tarefa (ex.: specs/001-…, T042). -->

## Como testar

<!-- Comandos ou passos do quickstart que comprovam a mudança. -->

## Checklist da constituição

Marque cada item depois de verificar. A aprovação manual (environment `aprovacao-manual`) só deve
ser dada com todos marcados ou com a justificativa escrita abaixo.

- [ ] **I. Sigilo** — nenhuma resposta, log, erro, evento SSE ou tela expõe o par de outro
      usuário; só `my-secret-friend` devolve um amigo secreto, e sempre o do usuário da sessão
- [ ] **II. Sorteio justo** — regras do sorteio intactas (mínimo de 3, ninguém tira a si mesmo,
      `SecureRandom`, atômico e definitivo)
- [ ] **III. Segurança** — rotas novas autenticadas (exceto as públicas da constituição), entrada
      validada, sem segredos no código, cookies/CSRF/cabeçalhos preservados
- [ ] **IV. Testes** — testes novos ou atualizados; cobertura ≥ 80% (CI verde)
- [ ] **V. Varredura** — CodeQL, Trivy, npm audit e Gitleaks sem HIGH/CRITICAL
- [ ] **VI. Mobile-first** — funciona em 360px, alvos de 44px, contraste AA, textos em pt-BR
- [ ] **VII. Containerização** — `docker compose up -d --build` continua funcionando; versões
      fixadas; imagens sem root
- [ ] **VIII. Simplicidade** — sem dependência nova sem justificativa no `plan.md`

## Observações

<!-- Riscos, pendências ou justificativa de algum item não marcado. -->
