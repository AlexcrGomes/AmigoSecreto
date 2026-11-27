import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useForm } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';
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
    // O erro não depende só da cor: vem com ícone
    expect(message.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
  });

  it('a dica também entra no aria-describedby', () => {
    render(<Input label="Senha" hint="De 8 a 64 caracteres." error="Senha curta." id="senha" />);

    const input = screen.getByLabelText('Senha');

    expect(input).toHaveAttribute('id', 'senha');
    expect(input).toHaveAccessibleDescription('De 8 a 64 caracteres. Senha curta.');
  });

  it('é um <input> nativo com as classes de vidro da Glisse (research R46)', () => {
    render(<Input label="Nome" />);

    const input = screen.getByLabelText('Nome');
    expect(input.tagName).toBe('INPUT');
    expect(input).toHaveClass('lg-glass', 'lg-input');
  });
});

type Field = 'text' | 'email' | 'password' | 'date' | 'time' | 'number';

const SAMPLES: Record<Field, { initial: string; typed: string; reset: string }> = {
  text: { initial: 'Natal', typed: 'Ceia', reset: 'Réveillon' },
  email: { initial: 'a@x.com', typed: 'b@x.com', reset: 'c@x.com' },
  password: { initial: 'segredo-1', typed: 'segredo-2', reset: 'segredo-3' },
  date: { initial: '2026-12-24', typed: '2026-12-25', reset: '2026-12-31' },
  time: { initial: '19:30', typed: '20:00', reset: '21:15' },
  number: { initial: '50', typed: '80', reset: '120' },
};

function Form({
  type,
  onSubmit,
  resetTo,
}: {
  type: Field;
  onSubmit: (value: string) => void;
  resetTo: string;
}) {
  const { register, handleSubmit, reset } = useForm<{ campo: string }>({
    defaultValues: { campo: SAMPLES[type].initial },
  });
  return (
    <form onSubmit={handleSubmit((data) => onSubmit(String(data.campo)))}>
      <Input label="Campo" type={type} {...register('campo')} />
      <button type="button" onClick={() => reset({ campo: resetTo })}>
        Recarregar
      </button>
      <button type="submit">Salvar</button>
    </form>
  );
}

describe('Input com react-hook-form', () => {
  it.each(Object.keys(SAMPLES) as Field[])(
    'em %s: mostra o defaultValues, envia o valor digitado e aceita reset()',
    async (type) => {
      const sample = SAMPLES[type];
      const asValue = (value: string) => (type === 'number' ? Number(value) : value);
      const onSubmit = vi.fn();
      render(<Form type={type} onSubmit={onSubmit} resetTo={sample.reset} />);
      const input = screen.getByLabelText('Campo');

      expect(input).toHaveValue(asValue(sample.initial));

      if (type === 'date' || type === 'time') {
        fireEvent.change(input, { target: { value: sample.typed } });
      } else {
        await userEvent.clear(input);
        await userEvent.type(input, sample.typed);
      }
      await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
      expect(onSubmit).toHaveBeenLastCalledWith(sample.typed);

      await userEvent.click(screen.getByRole('button', { name: 'Recarregar' }));
      expect(input).toHaveValue(asValue(sample.reset));
    },
  );
});
