import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';

describe('Button', () => {
  it('chama onClick e tem alvo de toque de pelo menos 44px', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Salvar</Button>);

    const button = screen.getByRole('button', { name: 'Salvar' });
    await userEvent.click(button);

    expect(onClick).toHaveBeenCalledTimes(1);
    expect(button).toHaveAttribute('type', 'button');
    expect(button.className).toContain('min-h-11');
  });

  it('em loading fica desabilitado, com aria-busy e spinner', async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Salvar
      </Button>,
    );

    const button = screen.getByRole('button', { name: /Salvar/ });
    await userEvent.click(button);

    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    expect(onClick).not.toHaveBeenCalled();
  });

  it('sem loading não mostra spinner nem aria-busy', () => {
    render(<Button variant="danger">Remover</Button>);

    expect(screen.queryByTestId('spinner')).not.toBeInTheDocument();
    expect(screen.getByRole('button')).not.toHaveAttribute('aria-busy');
  });

  it.each(['primary', 'secondary', 'danger', 'ghost'] as const)(
    'aceita a variante %s',
    (variant) => {
      render(
        <Button variant={variant} fullWidth>
          Ok
        </Button>,
      );

      expect(screen.getByRole('button', { name: 'Ok' }).className).toContain('w-full');
    },
  );
});
