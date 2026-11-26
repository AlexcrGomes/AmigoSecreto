import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { useGroup } from '../../hooks/useGroup';
import { BRUNO_ID, openGroup, problem } from '../../test/groups';
import { ANA, mockSession, renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { ConfirmParticipationCard } from './ConfirmParticipationCard';

/** Ana entrou pelo código e ainda não confirmou; Bruno é o organizador. */
const PENDING = openGroup({
  organizer: false,
  myConfirmation: 'PENDING',
  confirmedCount: 1,
  detailsVersion: 3,
  participants: [
    { userId: BRUNO_ID, name: 'Bruno', organizer: true, confirmed: true },
    { userId: ANA.id, name: 'Ana', organizer: false, confirmed: false },
  ],
});

function renderCard(onLeave = vi.fn()) {
  mockSession(ANA);
  renderWithProviders(<ConfirmParticipationCard group={PENDING} onLeave={onLeave} />);
  return onLeave;
}

/** O cartão com a consulta do grupo ativa, como na tela do grupo. */
function WithLiveGroup() {
  const group = useGroup('g-1');
  return group.data ? <ConfirmParticipationCard group={group.data} onLeave={vi.fn()} /> : null;
}

describe('ConfirmParticipationCard', () => {
  it('mostra a data, o local e o botão "Confirmar participação"', () => {
    renderCard();

    expect(screen.getByRole('heading', { name: 'Confirme sua participação' })).toBeInTheDocument();
    expect(screen.getByText(/sábado, 19 de dezembro de 2026, às 19:00/)).toBeInTheDocument();
    expect(screen.getByText(/Casa da Ana/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar participação' })).toBeEnabled();
  });

  it('confirma a versão vista, com carregando, e avisa', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/groups/g-1/confirmation', async ({ request }) => {
        body = await request.json();
        await delay(50);
        return HttpResponse.json({ ...PENDING, myConfirmation: 'CONFIRMED', confirmedCount: 2 });
      }),
    );
    renderCard();

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar participação' }));

    expect(screen.getByRole('button', { name: /Confirmar participação/ })).toHaveAttribute(
      'aria-busy',
      'true',
    );
    expect(await screen.findByText('Participação confirmada!')).toBeInTheDocument();
    expect(body).toEqual({ detailsVersion: 3 });
  });

  it('details-changed recarrega o grupo e pede para conferir', async () => {
    let reloads = 0;
    server.use(
      http.post('/api/v1/groups/g-1/confirmation', () =>
        problem(
          409,
          'Os detalhes do evento mudaram. Confira e confirme de novo.',
          '/problems/details-changed',
        ),
      ),
      http.get('/api/v1/groups/g-1', () => {
        reloads += 1;
        return HttpResponse.json({ ...PENDING, detailsVersion: 4 });
      }),
    );
    mockSession(ANA);
    renderWithProviders(<WithLiveGroup />);

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar participação' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Os detalhes mudaram. Confira e confirme de novo.',
    );
    // A primeira consulta abriu a tela; a segunda é a recarga depois da recusa
    await vi.waitFor(() => expect(reloads).toBe(2));
  });

  it('outros erros aparecem no cartão (FR-024)', async () => {
    server.use(
      http.post('/api/v1/groups/g-1/confirmation', () =>
        problem(409, 'O sorteio deste grupo já foi realizado.', '/problems/group-already-drawn'),
      ),
    );
    renderCard();

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar participação' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O sorteio deste grupo já foi realizado.',
    );
  });

  it('"Não vou participar" pede para sair do grupo', async () => {
    const onLeave = renderCard();

    await userEvent.click(screen.getByRole('button', { name: 'Não vou participar' }));

    expect(onLeave).toHaveBeenCalledTimes(1);
  });

  it('com dependentes, o botão diz que eles confirmam junto (feature 004)', () => {
    mockSession(ANA);
    const dependents = ['Lucas', 'Bia', 'Davi'].map((name, index) => ({
      id: `d-${index}`,
      name,
      confirmed: false,
      wishlist: { items: [], maxItems: 5, editable: true },
      exitRequest: null,
    }));
    renderWithProviders(
      <ConfirmParticipationCard
        group={{ ...PENDING, myDependents: dependents.slice(0, 1) }}
        onLeave={vi.fn()}
      />,
    );
    expect(
      screen.getByRole('button', { name: 'Confirmar participação (você e Lucas)' }),
    ).toBeInTheDocument();
  });

  it('com vários dependentes, junta os nomes', () => {
    mockSession(ANA);
    const dependents = ['Lucas', 'Bia', 'Davi'].map((name, index) => ({
      id: `d-${index}`,
      name,
      confirmed: false,
      wishlist: { items: [], maxItems: 5, editable: true },
      exitRequest: null,
    }));
    renderWithProviders(
      <ConfirmParticipationCard
        group={{ ...PENDING, myDependents: dependents }}
        onLeave={vi.fn()}
      />,
    );
    expect(
      screen.getByRole('button', { name: 'Confirmar participação (você e Lucas, Bia e Davi)' }),
    ).toBeInTheDocument();
  });
});
