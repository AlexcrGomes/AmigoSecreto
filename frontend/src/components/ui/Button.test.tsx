import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button, buttonClasses } from './Button';

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

describe('Button sobre a Glisse (feature 005)', () => {
  it('primary é o botão preenchido da Glisse', () => {
    render(<Button>Criar grupo</Button>);

    const button = screen.getByRole('button', { name: 'Criar grupo' });
    expect(button).toHaveClass('lg-glass', 'lg-button');
    expect(button).toHaveAttribute('data-variant', 'filled');
    expect(button).not.toHaveAttribute('data-destructive');
  });

  it('danger é o vidro com a marca destrutiva', () => {
    render(<Button variant="danger">Remover</Button>);

    const button = screen.getByRole('button', { name: 'Remover' });
    expect(button).toHaveAttribute('data-destructive');
    expect(button).not.toHaveAttribute('data-variant');
  });

  it.each(['secondary', 'ghost'] as const)('%s é o vidro claro, sem preenchimento', (variant) => {
    render(<Button variant={variant}>Voltar</Button>);

    const button = screen.getByRole('button', { name: 'Voltar' });
    expect(button).toHaveClass('lg-button');
    expect(button).not.toHaveAttribute('data-variant');
    expect(button).not.toHaveAttribute('data-destructive');
  });

  it('respeita type="submit" quando pedido', () => {
    render(<Button type="submit">Enviar</Button>);

    expect(screen.getByRole('button', { name: 'Enviar' })).toHaveAttribute('type', 'submit');
  });

  it('buttonClasses continua servindo para links em pílula', () => {
    const classes = buttonClasses('primary', true);

    expect(classes).toContain('min-h-11');
    expect(classes).toContain('rounded-control');
    expect(classes).toContain('w-full');
    expect(buttonClasses('secondary')).toContain('surface-glass');
  });
});
