/**
 * Valores dos tokens de cor do design (feature 005, data-model.md §1–§2), fonte única para os testes
 * de contraste. O `index.css` usa os mesmos valores — `contrast.test.ts` confere que batem.
 */

export type ThemeName = 'light' | 'dark';

export interface ThemeTokens {
  text: string;
  textMuted: string;
  primary: string;
  primaryFill: string;
  onPrimary: string;
  success: string;
  danger: string;
  accentGreen: string;
  accentGold: string;
  surface: string;
}

/** Paleta festiva (FR-016): vermelho principal, verde e dourado de apoio. */
export const THEMES: Record<ThemeName, ThemeTokens> = {
  light: {
    text: '#1f1315',
    textMuted: '#5b4448',
    primary: '#b3121f',
    primaryFill: '#b3121f',
    onPrimary: '#ffffff',
    success: '#15803d',
    danger: '#be123c',
    accentGreen: '#15803d',
    accentGold: '#92400e',
    surface: '#ffffff',
  },
  dark: {
    text: '#fff7f5',
    textMuted: '#e7cfd2',
    primary: '#ff6b6b',
    primaryFill: '#c81e2b',
    onPrimary: '#ffffff',
    success: '#4ade80',
    danger: '#fb7185',
    accentGreen: '#4ade80',
    accentGold: '#fbbf24',
    surface: '#1c1416',
  },
};

/** Cor e opacidade do vidro em cada tema (`--color-glass`). */
export const GLASS: Record<ThemeName, { color: string; alpha: number }> = {
  light: { color: '#ffffff', alpha: 0.72 },
  dark: { color: '#1c1416', alpha: 0.66 },
};

/**
 * Cores que podem estar atrás do vidro: as paradas do degradê e as manchas de luz no ponto mais
 * forte (data-model.md §2). O contraste é medido contra todas — o pior caso decide.
 */
export const BACKGROUND_STOPS: Record<ThemeName, string[]> = {
  light: ['#fff7ed', '#fde2e4', '#fef3c7', '#fecaca', '#d1fae5', '#fde68a'],
  dark: ['#1c0a10', '#4c0519', '#064e3b', '#7f1d1d', '#14532d', '#78350f'],
};
