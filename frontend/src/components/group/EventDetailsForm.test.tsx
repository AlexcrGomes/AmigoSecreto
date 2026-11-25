import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { todayInBrasilia } from '../../lib/eventDate';
import { ANA, mockSession, renderWithProviders } from '../../test/render';
import { EventDetailsForm } from './EventDetailsForm';

/** Data `AAAA-MM-DD` a `days` dias de hoje em Brasília. */
function fromToday(days: number): string {
  const [y = 0, m = 1, d = 1] = todayInBrasilia().split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

function renderForm(props: Partial<Parameters<typeof EventDetailsForm>[0]> = {}) {
  mockSession(ANA);
  const onSubmit = props.onSubmit ?? vi.fn().mockResolvedValue(undefined);
  renderWithProviders(<EventDetailsForm submitLabel="Salvar" onSubmit={onSubmit} {...props} />);
  return { onSubmit };
}

async function fill(label: string, value: string) {
  const field = screen.getByLabelText(label);
  await userEvent.clear(field);
  if (value) await userEvent.type(field, value);
}

describe('EventDetailsForm', () => {
  it('data e local são obrigatórios', async () => {
    const { onSubmit } = renderForm();

    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Informe a data do evento.')).toBeInTheDocument();
    expect(screen.getByText('Informe o local do evento (até 120 caracteres).')).toBeInTheDocument();
    expect(screen.getByLabelText('Data do evento')).toHaveAttribute('aria-invalid', 'true');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('recusa data de ontem e horário de hoje que já passou', async () => {
    const { onSubmit } = renderForm();
    await fill('Local do evento', 'Casa');

    await fill('Data do evento', fromToday(-1));
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(
      await screen.findByText('A data do evento precisa ser hoje ou no futuro.'),
    ).toBeInTheDocument();

    await fill('Data do evento', fromToday(0));
    await fill('Horário (opcional)', '00:00');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    expect(await screen.findByText('Esse horário já passou.')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('conta os caracteres da descrição e recusa mais de 500', async () => {
    const { onSubmit } = renderForm();
    expect(screen.getByText('0/500')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Descrição (opcional)'), 'Amigo');
    expect(screen.getByText('5/500')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Descrição (opcional)'));
    await userEvent.click(screen.getByLabelText('Descrição (opcional)'));
    await userEvent.paste('x'.repeat(501));
    await fill('Data do evento', fromToday(10));
    await fill('Local do evento', 'Casa');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('A descrição pode ter até 500 caracteres.')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('envia os detalhes limpos: vazio vira null, espaços saem das pontas', async () => {
    const { onSubmit } = renderForm({ withName: true });
    const date = fromToday(30);

    await fill('Nome do grupo', '  Natal  ');
    await fill('Data do evento', date);
    await fill('Local do evento', '  Casa da Ana  ');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await vi.waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      {
        description: null,
        eventDate: date,
        eventTime: null,
        location: 'Casa da Ana',
        giftValue: null,
      },
      'Natal',
    );
  });

  it('ao editar, a data já vencida só é validada se mudar', async () => {
    const past = fromToday(-3);
    const { onSubmit } = renderForm({
      initial: { description: 'Linha 1', eventDate: past, eventTime: '19:00', location: 'Casa' },
    });
    expect(screen.getByLabelText('Data do evento')).toHaveValue(past);
    expect(screen.getByLabelText('Horário (opcional)')).toHaveValue('19:00');

    await fill('Local do evento', 'Salão');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await vi.waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      {
        description: 'Linha 1',
        eventDate: past,
        eventTime: '19:00',
        location: 'Salão',
        giftValue: null,
      },
      '',
    );
  });

  it('mostra carregando enquanto salva e o erro do servidor depois (FR-024)', async () => {
    let fail: (error: unknown) => void = () => {};
    const onSubmit = vi.fn(
      () =>
        new Promise<void>((_, reject) => {
          fail = reject;
        }),
    );
    renderForm({ onSubmit });
    await fill('Data do evento', fromToday(5));
    await fill('Local do evento', 'Casa');

    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await vi.waitFor(() =>
      expect(screen.getByRole('button', { name: 'Salvar' })).toHaveAttribute('aria-busy', 'true'),
    );
    fail(
      new ApiError(422, {
        type: '/problems/event-date-in-past',
        detail: 'A data do evento precisa ser hoje ou no futuro.',
      }),
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A data do evento precisa ser hoje ou no futuro.',
    );
    expect(screen.getByRole('button', { name: 'Salvar' })).not.toHaveAttribute('aria-busy');
  });

  // ---- valor do presente (US3 da 003) ----

  it('envia o valor do presente como número e vazio como null', async () => {
    const date = fromToday(10);
    const { onSubmit } = renderForm();
    await fill('Data do evento', date);
    await fill('Local do evento', 'Casa');
    await fill('Valor do presente (R$, opcional)', '80');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await vi.waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      { description: null, eventDate: date, eventTime: null, location: 'Casa', giftValue: 80 },
      '',
    );
  });

  it('recusa valor zero, decimal ou acima de 100.000', async () => {
    const { onSubmit } = renderForm();
    await fill('Data do evento', fromToday(10));
    await fill('Local do evento', 'Casa');

    for (const invalid of ['0', '50,5', '100001']) {
      await fill('Valor do presente (R$, opcional)', invalid);
      await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
      expect(
        await screen.findByText('Informe um valor inteiro entre R$ 1 e R$ 100.000.'),
      ).toBeInTheDocument();
    }
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('mudar só o valor não mostra o aviso de confirmar de novo', async () => {
    renderForm({
      initial: {
        description: null,
        eventDate: fromToday(10),
        eventTime: null,
        location: 'Casa',
        giftValue: 80,
      },
      scheduleChangeWarning: 'Todos os participantes precisarão confirmar de novo.',
    });
    expect(screen.getByLabelText('Valor do presente (R$, opcional)')).toHaveValue('80');

    await fill('Valor do presente (R$, opcional)', '100');

    expect(
      screen.queryByText('Todos os participantes precisarão confirmar de novo.'),
    ).not.toBeInTheDocument();
  });

  it('depois do sorteio o valor fica travado, com a explicação, e é reenviado igual', async () => {
    const date = fromToday(10);
    const { onSubmit } = renderForm({
      initial: {
        description: null,
        eventDate: date,
        eventTime: null,
        location: 'Casa',
        giftValue: 80,
      },
      giftValueLocked: true,
    });
    const field = screen.getByLabelText('Valor do presente (R$, opcional)');
    expect(field).toHaveAttribute('readonly');
    expect(screen.getByText('O valor do presente não muda depois do sorteio.')).toBeInTheDocument();

    await userEvent.type(field, '5');
    await fill('Local do evento', 'Salão');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await vi.waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith(
      { description: null, eventDate: date, eventTime: null, location: 'Salão', giftValue: 80 },
      '',
    );
  });
});
