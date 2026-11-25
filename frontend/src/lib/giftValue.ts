/** "R$ 1.000": valor do presente em reais inteiros (feature 003). */
export function formatGiftValue(value: number): string {
  return `R$ ${value.toLocaleString('pt-BR')}`;
}
