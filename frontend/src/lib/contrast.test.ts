import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { contrastRatio, composite } from './contrast';
import { BACKGROUND_STOPS, GLASS, THEMES, type ThemeName } from './designTokens';

const AA_TEXT = 4.5;

// Lido do disco (os testes rodam a partir de frontend/): no Vitest, `?raw` de um .css vem vazio
const css = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf-8');

/** Pares texto/fundo que aparecem nas telas (data-model.md §1). */
const TEXT_ON_GLASS = ['text', 'textMuted', 'primary', 'success', 'danger', 'accentGold'] as const;

describe('contraste (WCAG 2.1)', () => {
  it('preto sobre branco é 21 e cores iguais dão 1', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 1);
    expect(contrastRatio('#b3121f', '#b3121f')).toBeCloseTo(1, 5);
  });

  it('compõe uma cor translúcida sobre o fundo', () => {
    expect(composite('#ffffff', 0.5, '#000000')).toBe('#808080');
    expect(composite('#ff0000', 1, '#00ff00')).toBe('#ff0000');
    expect(composite('#ff0000', 0, '#00ff00')).toBe('#00ff00');
  });

  describe.each(Object.keys(THEMES) as ThemeName[])('tema %s', (theme) => {
    const tokens = THEMES[theme];

    it.each(TEXT_ON_GLASS)('%s sobre o vidro, no pior ponto do fundo, passa AA', (pair) => {
      for (const stop of BACKGROUND_STOPS[theme]) {
        const glass = composite(GLASS[theme].color, GLASS[theme].alpha, stop);
        expect(contrastRatio(tokens[pair], glass), `${pair} sobre ${stop}`).toBeGreaterThanOrEqual(
          AA_TEXT,
        );
      }
    });

    it.each(TEXT_ON_GLASS)('%s sobre a superfície sólida passa AA', (pair) => {
      expect(contrastRatio(tokens[pair], tokens.surface)).toBeGreaterThanOrEqual(AA_TEXT);
    });

    it('texto do botão principal sobre o preenchimento passa AA', () => {
      expect(contrastRatio(tokens.onPrimary, tokens.primaryFill)).toBeGreaterThanOrEqual(AA_TEXT);
    });
  });

  it('o index.css usa exatamente os valores de designTokens.ts nos dois temas', () => {
    const [light, dark] = css.split('@media (prefers-color-scheme: dark)');
    const names: Record<keyof (typeof THEMES)['light'], string> = {
      text: 'text',
      textMuted: 'text-muted',
      primary: 'primary',
      primaryFill: 'primary-fill',
      onPrimary: 'on-primary',
      success: 'success',
      danger: 'danger',
      accentGreen: 'accent-green',
      accentGold: 'accent-gold',
      surface: 'surface',
    };
    for (const [key, name] of Object.entries(names)) {
      const k = key as keyof typeof names;
      expect(light, `claro: --color-${name}`).toContain(`--color-${name}: ${THEMES.light[k]};`);
      expect(dark, `escuro: --color-${name}`).toContain(`--color-${name}: ${THEMES.dark[k]};`);
    }
  });
});
