import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AppBackground } from './AppBackground';

describe('AppBackground', () => {
  it('é decorativo e marcado para a refração da Glisse', () => {
    render(<AppBackground />);

    const background = screen.getByTestId('app-background');
    expect(background).toHaveAttribute('aria-hidden', 'true');
    expect(background).toHaveAttribute('data-glisse-backdrop');
    expect(background).toHaveTextContent('');
  });

  it('tem brilhos vermelho e dourados, sem verde (revisão 2026-11-27)', () => {
    render(<AppBackground />);

    const background = screen.getByTestId('app-background');
    expect(background.querySelectorAll('.app-spot-red')).toHaveLength(1);
    expect(background.querySelectorAll('.app-spot-gold')).toHaveLength(2);
    expect(background.innerHTML).not.toMatch(/green/i);
  });
});
