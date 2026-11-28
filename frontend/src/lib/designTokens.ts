/**
 * Valores dos tokens de cor do design (feature 005, data-model.md §1–§2), fonte única para os testes
 * de contraste. O `index.css` usa os mesmos valores — `contrast.test.ts` confere que batem.
 *
 * Revisão de 2026-11-27 (research R55): um tema só, claro — branco e vermelho com detalhes
 * dourados, sem verde.
 */

export interface DesignTokens {
  text: string;
  textMuted: string;
  primary: string;
  primaryFill: string;
  onPrimary: string;
  /** Sucesso é dourado, sempre com ícone ou texto. */
  success: string;
  danger: string;
  /** Dourado para texto e ícones. */
  accentGold: string;
  /** Dourado decorativo: nunca leva texto. */
  goldSoft: string;
  surface: string;
}

export const TOKENS: DesignTokens = {
  text: '#1f1315',
  textMuted: '#5b4448',
  primary: '#b3121f',
  primaryFill: '#b3121f',
  onPrimary: '#ffffff',
  success: '#a16207',
  danger: '#be123c',
  accentGold: '#a16207',
  goldSoft: '#e9c46a',
  surface: '#ffffff',
};

/** Cor e opacidade do vidro (`--color-glass`). */
export const GLASS = { color: '#ffffff', alpha: 0.78 };

/**
 * Cores que podem estar atrás do vidro: as paradas do degradê e as manchas de luz no ponto mais
 * forte (data-model.md §2). O contraste é medido contra todas — o pior caso decide.
 */
export const BACKGROUND_STOPS = ['#ffffff', '#fffaf3', '#fdf3e3', '#f7e7e9', '#f5e2ba'];
