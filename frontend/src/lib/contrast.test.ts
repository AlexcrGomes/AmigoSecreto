import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { contrastRatio, composite } from './contrast';
import { BACKGROUND_STOPS, GLASS, TOKENS } from './designTokens';

const AA_TEXT = 4.5;

// Lidos do disco (os testes rodam a partir de frontend/): no Vitest, `?raw` de um .css vem vazio
const css = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf-8');
const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf-8');

/** Pares texto/fundo que aparecem nas telas (data-model.md §1). */
const TEXT_ON_GLASS = ['text', 'textMuted', 'primary', 'accentGold', 'success', 'danger'] as const;

describe('contraste (WCAG 2.1) — tema único branco, vermelho e dourado', () => {
  it('preto sobre branco é 21 e cores iguais dão 1', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 1);
    expect(contrastRatio('#b3121f', '#b3121f')).toBeCloseTo(1, 5);
  });

  it('compõe uma cor translúcida sobre o fundo', () => {
    expect(composite('#ffffff', 0.5, '#000000')).toBe('#808080');
    expect(composite('#ff0000', 1, '#00ff00')).toBe('#ff0000');
    expect(composite('#ff0000', 0, '#00ff00')).toBe('#00ff00');
  });

  it.each(TEXT_ON_GLASS)('%s sobre o vidro, no pior ponto do fundo, passa AA', (pair) => {
    for (const stop of BACKGROUND_STOPS) {
      const glass = composite(GLASS.color, GLASS.alpha, stop);
      expect(contrastRatio(TOKENS[pair], glass), `${pair} sobre ${stop}`).toBeGreaterThanOrEqual(
        AA_TEXT,
      );
    }
  });

  it.each(TEXT_ON_GLASS)('%s sobre a superfície sólida passa AA', (pair) => {
    expect(contrastRatio(TOKENS[pair], TOKENS.surface)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('texto do botão principal sobre o preenchimento passa AA', () => {
    expect(contrastRatio(TOKENS.onPrimary, TOKENS.primaryFill)).toBeGreaterThanOrEqual(AA_TEXT);
  });

  it('o index.css usa exatamente os valores de designTokens.ts', () => {
    const names: Record<keyof typeof TOKENS, string> = {
      text: 'text',
      textMuted: 'text-muted',
      primary: 'primary',
      primaryFill: 'primary-fill',
      onPrimary: 'on-primary',
      success: 'success',
      danger: 'danger',
      accentGold: 'accent-gold',
      goldSoft: 'gold-soft',
      surface: 'surface',
    };
    for (const [key, name] of Object.entries(names)) {
      const value = TOKENS[key as keyof typeof TOKENS];
      expect(css, `--color-${name}`).toContain(`--color-${name}: ${value};`);
    }
  });

  it('não há tema escuro nem verde (research R55)', () => {
    expect(css).not.toContain('prefers-color-scheme: dark');
    expect(css).not.toMatch(/green/i);
  });

  it('o index.html trava o tema claro da Glisse e tem uma só cor de barra', () => {
    expect(html).toMatch(/<html[^>]*data-theme="light"/);
    expect(html.match(/name="theme-color"/g)).toHaveLength(1);
    expect(html).toContain('<meta name="theme-color" content="#ffffff" />');
  });
});
