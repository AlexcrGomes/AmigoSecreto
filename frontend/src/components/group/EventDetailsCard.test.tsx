import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EventDetailsCard } from './EventDetailsCard';

const DETAILS = {
  description: 'Traga um prato.\nValor: R$ 50',
  eventDate: '2026-12-19',
  eventTime: '19:00',
  location: '<b>casa</b>',
};

describe('EventDetailsCard', () => {
  it('mostra a data por extenso, o local e a descrição com as quebras de linha', () => {
    render(<EventDetailsCard details={DETAILS} />);

    expect(screen.getByRole('heading', { name: 'Detalhes do evento' })).toBeInTheDocument();
    expect(screen.getByText('sábado, 19 de dezembro de 2026, às 19:00')).toBeInTheDocument();
    const description = screen.getByText(/Traga um prato/);
    expect(description.textContent).toBe('Traga um prato.\nValor: R$ 50');
    expect(description).toHaveClass('whitespace-pre-line');
  });

  it('texto digitado nunca vira HTML (FR-007)', () => {
    const { container } = render(<EventDetailsCard details={DETAILS} />);

    expect(screen.getByText('<b>casa</b>')).toBeInTheDocument();
    expect(container.querySelector('b')).toBeNull();
  });

  it('sem horário nem descrição mostra só a data e o local', () => {
    render(
      <EventDetailsCard
        details={{ ...DETAILS, eventTime: null, description: null, location: 'Salão' }}
      />,
    );

    expect(screen.getByText('sábado, 19 de dezembro de 2026')).toBeInTheDocument();
    expect(screen.getByText('Salão')).toBeInTheDocument();
    expect(screen.queryByText(/Traga/)).not.toBeInTheDocument();
  });

  it('"Editar detalhes" só aparece quando há onEdit (organizador)', async () => {
    const onEdit = vi.fn();
    const { rerender } = render(<EventDetailsCard details={DETAILS} />);
    expect(screen.queryByRole('button', { name: 'Editar detalhes' })).not.toBeInTheDocument();

    rerender(<EventDetailsCard details={DETAILS} onEdit={onEdit} />);
    await userEvent.click(screen.getByRole('button', { name: 'Editar detalhes' }));

    expect(onEdit).toHaveBeenCalledTimes(1);
  });

  // ---- valor do presente (US3 da 003) ----

  it('mostra o valor do presente quando informado', () => {
    render(<EventDetailsCard details={{ ...DETAILS, giftValue: 1500 }} />);

    expect(screen.getByText('Valor do presente')).toBeInTheDocument();
    expect(screen.getByText('R$ 1.500')).toBeInTheDocument();
  });

  it('sem valor, não mostra a linha', () => {
    render(<EventDetailsCard details={{ ...DETAILS, giftValue: null }} />);

    expect(screen.queryByText('Valor do presente')).not.toBeInTheDocument();
  });
});
