import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Card } from './Card';

describe('Card', () => {
  it('comum usa o vidro fosco do projeto', () => {
    render(<Card aria-label="Detalhes">Conteúdo</Card>);

    const card = screen.getByLabelText('Detalhes');
    expect(card).toHaveClass('surface-glass');
    expect(card).not.toHaveClass('lg-glass');
    expect(card).toHaveTextContent('Conteúdo');
  });

  it('com highlight usa a superfície de vidro da Glisse', () => {
    render(
      <Card highlight aria-label="Seu amigo secreto">
        Ana
      </Card>,
    );

    const card = screen.getByLabelText('Seu amigo secreto');
    expect(card).toHaveClass('lg-glass');
    expect(card).toHaveTextContent('Ana');
  });
});
