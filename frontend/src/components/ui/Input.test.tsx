import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Input } from './Input';

describe('Input', () => {
  it('associa o label ao campo', async () => {
    render(<Input label="E-mail" type="email" />);

    const input = screen.getByLabelText('E-mail');
    await userEvent.type(input, 'ana@x.com');

    expect(input).toHaveValue('ana@x.com');
    expect(input).not.toHaveAttribute('aria-invalid');
    expect(input.className).toContain('min-h-11');
  });

  it('com erro marca aria-invalid e liga a mensagem via aria-describedby', () => {
    render(<Input label="E-mail" error="E-mail inválido." />);

    const input = screen.getByLabelText('E-mail');
    const message = screen.getByText('E-mail inválido.');

    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input.getAttribute('aria-describedby')).toContain(message.id);
    expect(input).toHaveAccessibleDescription('E-mail inválido.');
  });

  it('a dica também entra no aria-describedby', () => {
    render(<Input label="Senha" hint="De 8 a 64 caracteres." error="Senha curta." id="senha" />);

    const input = screen.getByLabelText('Senha');

    expect(input).toHaveAttribute('id', 'senha');
    expect(input).toHaveAccessibleDescription('De 8 a 64 caracteres. Senha curta.');
  });
});
