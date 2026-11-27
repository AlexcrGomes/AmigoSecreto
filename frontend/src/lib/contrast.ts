/**
 * Contraste de cores pela WCAG 2.1 (feature 005, research R52). Usado nos testes que provam que os
 * tokens do design passam AA nos dois temas, inclusive sobre o vidro.
 */

function channels(hex: string): [number, number, number] {
  const value = hex.replace('#', '');
  return [0, 2, 4].map((at) => parseInt(value.slice(at, at + 2), 16)) as [number, number, number];
}

/** Luminância relativa (0 = preto, 1 = branco). */
export function relativeLuminance(hex: string): number {
  const [r, g, b] = channels(hex).map((channel) => {
    const c = channel / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Razão de contraste entre duas cores opacas, de 1 a 21. */
export function contrastRatio(a: string, b: string): number {
  const [light, dark] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x) as [
    number,
    number,
  ];
  return (light + 0.05) / (dark + 0.05);
}

/** A cor `color` com opacidade `alpha` pintada sobre `background` (ambas em #rrggbb). */
export function composite(color: string, alpha: number, background: string): string {
  const front = channels(color);
  const back = channels(background);
  return (
    '#' +
    front
      .map((channel, i) =>
        Math.round(channel * alpha + (back[i] ?? 0) * (1 - alpha))
          .toString(16)
          .padStart(2, '0'),
      )
      .join('')
  );
}
