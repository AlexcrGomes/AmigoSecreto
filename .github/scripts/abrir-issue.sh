#!/usr/bin/env bash
# Abre (ou atualiza com um comentário) a issue da varredura de segurança semanal que falhou.
# Uso: bash .github/scripts/abrir-issue.sh <pasta-dos-relatorios>
# Variáveis: GH_TOKEN, GH_REPO, RUN_URL e RESULT_TRIVY_FS, RESULT_TRIVY_IMAGE,
# RESULT_NPM_AUDIT, RESULT_GITLEAKS (resultado de cada job: success, failure…).
# Com DRY_RUN=1 só imprime o corpo da issue, sem chamar o GitHub.
set -euo pipefail

pasta="${1:-relatorios}"
titulo="Varredura de segurança semanal encontrou problemas"
corpo=$(mktemp)

{
  echo "A varredura de segurança de **$(date -u +%Y-%m-%d)** reprovou. Detalhes e logs: ${RUN_URL:-}"
  echo
  echo "| Verificação | Resultado |"
  echo "|---|---|"
  echo "| Trivy: dependências e segredos (\`trivy-fs\`) | ${RESULT_TRIVY_FS:-?} |"
  echo "| Trivy: imagens Docker (\`trivy-image\`) | ${RESULT_TRIVY_IMAGE:-?} |"
  echo "| npm audit (\`npm-audit\`) | ${RESULT_NPM_AUDIT:-?} |"
  echo "| Gitleaks: segredos no histórico (\`gitleaks\`) | ${RESULT_GITLEAKS:-?} |"
  echo

  for relatorio in "$pasta"/relatorio-*.md; do
    [ -f "$relatorio" ] || continue
    cat "$relatorio"
    echo
  done

  if [ "${RESULT_GITLEAKS:-}" = "failure" ]; then
    echo "### Gitleaks"
    echo
    echo "Possível segredo no histórico do git. Por segurança o valor não é copiado para cá: veja o"
    echo "log do job \`gitleaks\` (valores ocultados) e **troque o segredo** antes de removê-lo do código."
    echo
  fi

  echo "Uma verificação que falhou sem tabela acima teve outro problema (rede, build): veja o log."
  echo
  echo "**Como resolver:** aceite o PR do Dependabot, se houver, ou atualize para a \"Versão corrigida\""
  echo "indicada; nos pacotes das imagens, reconstruir costuma bastar (os Dockerfiles rodam \`apk upgrade\`)."
  echo "Feche esta issue pelo PR da correção, com \`Closes #<número>\` na descrição."
} >"$corpo"

# Limite do GitHub para o corpo de uma issue (65536 caracteres)
corpo_final=$(mktemp)
head -c 60000 "$corpo" >"$corpo_final"

if [ "${DRY_RUN:-0}" = "1" ]; then
  cat "$corpo_final"
  exit 0
fi

gh label create seguranca --color B60205 \
  --description "Vulnerabilidades e segredos encontrados pelas varreduras" --force

# Uma issue aberta por vez: se já existir, a nova falha vira comentário nela
numero=$(gh issue list --state open --label seguranca --limit 100 --json number,title \
  --jq ".[] | select(.title == \"$titulo\") | .number" | head -n 1)

if [ -n "$numero" ]; then
  gh issue comment "$numero" --body-file "$corpo_final"
  echo "Issue #$numero atualizada com um novo comentário."
else
  gh issue create --title "$titulo" --label seguranca --body-file "$corpo_final"
fi
