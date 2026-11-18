#!/usr/bin/env bash
# Converte relatórios JSON do Trivy numa tabela Markdown (CVEs e segredos).
# Uso: bash .github/scripts/trivy-resumo.sh <saida.md> <relatorio.json>...
# Escreve <saida.md>, acrescenta o mesmo conteúdo ao resumo do job (se houver) e
# termina com código 1 quando existe ao menos um achado — é isso que reprova o check.
set -euo pipefail

saida="$1"
shift

total=0
: >"$saida"

for relatorio in "$@"; do
  vulns=$(jq '[.Results[]? | (.Vulnerabilities // [])[]] | length' "$relatorio")
  segredos=$(jq '[.Results[]? | (.Secrets // [])[]] | length' "$relatorio")
  total=$((total + vulns + segredos))
  [ $((vulns + segredos)) -eq 0 ] && continue

  {
    echo "### \`$(jq -r '.ArtifactName' "$relatorio")\`"
    echo

    if [ "$vulns" -gt 0 ]; then
      echo "| CVE | Gravidade | Pacote | Versão instalada | Versão corrigida | Onde |"
      echo "|---|---|---|---|---|---|"
      jq -r '
        .Results[]? | .Target as $alvo | (.Vulnerabilities // [])[] |
        "| [\(.VulnerabilityID)](\(.PrimaryURL // ("https://avd.aquasec.com/nvd/" + (.VulnerabilityID | ascii_downcase))))"
        + " | \(.Severity) | `\(.PkgName)` | \(.InstalledVersion) | \(.FixedVersion // "-") | `\($alvo)` |"
      ' "$relatorio"
      echo
    fi

    if [ "$segredos" -gt 0 ]; then
      # Nunca publica o valor do segredo: só a regra e o local
      echo "| Segredo | Gravidade | Arquivo:linha |"
      echo "|---|---|---|"
      jq -r '
        .Results[]? | .Target as $alvo | (.Secrets // [])[] |
        "| \(.Title) (`\(.RuleID)`) | \(.Severity) | `\($alvo):\(.StartLine)` |"
      ' "$relatorio"
      echo
    fi
  } >>"$saida"
done

if [ "$total" -eq 0 ]; then
  echo "Nenhum achado HIGH/CRITICAL com correção disponível." >>"$saida"
fi

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  cat "$saida" >>"$GITHUB_STEP_SUMMARY"
fi
cat "$saida"

[ "$total" -eq 0 ]
