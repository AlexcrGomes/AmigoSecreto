# Como contribuir

Este projeto segue a [constituição](.specify/memory/constitution.md) e o fluxo SDD (Spec Kit).
Nenhuma mudança entra na `main` sem Pull Request, validação automática verde e aprovação manual.

## Fluxo de uma mudança

1. Crie uma branch a partir da `main` (`NNN-nome-da-feature` para features do Spec Kit).
2. Faça commits no padrão Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`…).
3. Abra o PR para a `main` e preencha o template, incluindo a checklist da constituição.
4. O GitHub roda automaticamente:
   - **CI** (`.github/workflows/ci.yml`): `backend` (testes, integração com Testcontainers,
     cobertura ≥ 80%, Spotless), `frontend` (lint, Prettier, testes, cobertura ≥ 80%, build) e
     `docker` (build das imagens).
   - **Security** (`.github/workflows/security.yml`): CodeQL, Trivy (dependências, segredos e
     imagens), `npm audit` e Gitleaks. Achados HIGH/CRITICAL reprovam.
5. Quando `backend`, `frontend` e `docker` passam, o job **`aprovacao`** fica aguardando o
   mantenedor. Revise o código e a checklist e aprove em:
   **aba Actions → execução do CI do PR → Review deployments → marcar `aprovacao-manual` → Approve and deploy**.
6. Com todos os checks verdes (inclusive `aprovacao` e os de segurança), o merge é liberado.

Todo novo commit no PR roda tudo de novo e **exige nova aprovação**, então a aprovação vale
sempre para o código final.

## Varredura semanal e issues de segurança

Toda segunda-feira, às 06:00 UTC, o workflow **Security** roda de novo, mesmo sem mudança no
código, porque novas vulnerabilidades (CVEs) são publicadas o tempo todo. Ele também pode ser
disparado à mão em **Actions → Security → Run workflow**.

Se o Trivy, o `npm audit` ou o Gitleaks reprovarem nessa execução, o job `abrir-issue` abre uma
issue **"Varredura de segurança semanal encontrou problemas"** com a etiqueta `seguranca`:

- uma tabela por imagem ou arquivo, com cada CVE (link para os detalhes), a gravidade, o pacote,
  a versão instalada e a **versão que corrige**;
- para segredos, só a regra e o arquivo:linha, nunca o valor;
- o link para a execução com os logs completos.

Só existe uma issue dessas aberta por vez: as falhas das semanas seguintes viram comentários
nela. Para resolver, aceite o PR do Dependabot ou atualize a versão indicada; nos pacotes das
imagens, reconstruir costuma bastar. Feche a issue pelo próprio PR da correção, escrevendo
`Closes #<número>` na descrição.

Enquanto a correção não entra, **todo PR fica bloqueado** nos checks de segurança, mesmo sem
relação com o problema. É intencional: nada novo entra na `main` com um HIGH/CRITICAL conhecido.

## Quando atualizar dependências

As versões ficam fixadas e **só mudam por dois motivos**:

1. **Falha de segurança registrada.** Os *security updates* do Dependabot estão ligados: quando
   sai um alerta para uma dependência Maven, npm ou GitHub Actions do projeto, ele abre sozinho
   um PR com a menor versão que corrige. Imagens Docker não têm alertas no GitHub; nelas quem
   avisa é o Trivy da varredura semanal, pela issue descrita acima.
2. **Uma funcionalidade nova precisa da versão nova.** A atualização entra no próprio PR da
   funcionalidade, com a justificativa no `plan.md`.

As atualizações de versão "por novidade" estão desligadas no `.github/dependabot.yml`
(`open-pull-requests-limit: 0`).

## Configuração única no GitHub (mantenedor)

O repositório é público, então o environment com revisor obrigatório está disponível no plano
gratuito. Faça uma vez:

### 1. Environment `aprovacao-manual`

**Settings → Environments → New environment** → nome `aprovacao-manual`:

- **Required reviewers**: adicione o seu usuário.
- Deixe **"Prevent self-review" desmarcado** (com um único mantenedor, quem aprova é o autor).
- **Deployment branches and tags**: *No restriction* (o job roda em branches de PR).

### 2. Ruleset da `main`

**Settings → Rules → Rulesets → New branch ruleset**:

- **Name**: `protecao-main`; **Enforcement status**: *Active*.
- **Bypass list**: vazia (nem administradores furam a regra).
- **Target branches**: *Include default branch*.
- Marque:
  - **Restrict deletions**
  - **Block force pushes**
  - **Require a pull request before merging** (aprovações exigidas: `0`, porque a aprovação manual
    é feita pelo environment; marque *Require conversation resolution before merging*)
  - **Require status checks to pass** e **Require branches to be up to date before merging**, com
    os checks: `backend`, `frontend`, `docker`, `aprovacao`, `codeql (java-kotlin)`,
    `codeql (javascript-typescript)`, `trivy-fs`, `npm-audit`, `trivy-image`, `gitleaks`

> Os nomes dos checks só aparecem na busca depois que os workflows rodarem uma vez (abra o
> primeiro PR e volte para completar a lista). **Não** inclua `abrir-issue`: ele só roda na
> varredura semanal.

### Quando houver um segundo mantenedor

Mude **Required approvals** do ruleset para `1` e marque **Require review from Code Owners** se
houver um `CODEOWNERS`. O environment pode continuar como uma segunda confirmação.

## Rodar localmente

Docker roda no WSL. Comandos completos em
[specs/001-amigo-secreto-sorteio/quickstart.md](specs/001-amigo-secreto-sorteio/quickstart.md).
