import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { BRUNO_ID, openGroup, problem } from '../../test/groups';
import { ANA, mockSession, renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { ExitRequestsList } from './ExitRequestsList';

/** Ana organiza; Bruno pediu para sair depois do sorteio. */
const WITH_REQUEST = openGroup({
  status: 'DRAWN',
  drawnAt: '2026-12-01T15:00:00Z',
  drawBlockedReason: null,
  pendingExitRequests: [
    { id: 'r-1', userId: BRUNO_ID, name: 'Bruno', requestedAt: '2026-12-02T15:00:00Z' },
  ],
});

function renderList(group = WITH_REQUEST) {
  mockSession(ANA);
  renderWithProviders(<ExitRequestsList group={group} />);
}

describe('ExitRequestsList', () => {
  it('lista os pedidos com nome, data e as duas ações', () => {
    renderList();

    const list = screen.getByRole('list', { name: 'Pedidos de saída' });
    const [bruno] = within(list).getAllByRole('listitem');
    expect(within(bruno as HTMLElement).getByText('Bruno')).toBeInTheDocument();
    expect(
      within(bruno as HTMLElement).getByText('Pediu para sair em 02/12/2026'),
    ).toBeInTheDocument();
    expect(
      within(bruno as HTMLElement).getByRole('button', { name: 'Aprovar' }),
    ).toBeInTheDocument();
    expect(
      within(bruno as HTMLElement).getByRole('button', { name: 'Recusar' }),
    ).toBeInTheDocument();
  });

  it('sem pedidos, não mostra nada', () => {
    renderList(openGroup({ status: 'DRAWN', pendingExitRequests: [] }));

    expect(screen.queryByRole('heading', { name: 'Pedidos de saída' })).not.toBeInTheDocument();
  });

  it('aprovar avisa que o sorteio será desfeito e confirma', async () => {
    let approved = false;
    server.use(
      http.post('/api/v1/groups/g-1/exit-requests/r-1/approve', () => {
        approved = true;
        return HttpResponse.json(
          openGroup({ status: 'OPEN', drawUndone: true, pendingExitRequests: [] }),
        );
      }),
    );
    renderList();

    await userEvent.click(screen.getByRole('button', { name: 'Aprovar' }));

    expect(screen.getByRole('dialog', { name: 'Aprovar a saída?' })).toHaveTextContent(
      'Bruno sairá do grupo, o sorteio será desfeito para todos e você precisará sortear de novo.',
    );
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Aprovar' }),
    );

    expect(
      await screen.findByText('Bruno saiu do grupo. Faça um novo sorteio.'),
    ).toBeInTheDocument();
    expect(approved).toBe(true);
  });

  it('recusar mantém a pessoa no grupo', async () => {
    let rejected = false;
    server.use(
      http.post('/api/v1/groups/g-1/exit-requests/r-1/reject', () => {
        rejected = true;
        return HttpResponse.json(openGroup({ status: 'DRAWN', pendingExitRequests: [] }));
      }),
    );
    renderList();

    await userEvent.click(screen.getByRole('button', { name: 'Recusar' }));
    expect(screen.getByRole('dialog', { name: 'Recusar o pedido?' })).toHaveTextContent(
      'Bruno continua no grupo, com o mesmo amigo secreto.',
    );
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Recusar' }),
    );

    expect(await screen.findByText('Pedido de saída recusado.')).toBeInTheDocument();
    expect(rejected).toBe(true);
  });

  it('erro na decisão aparece no diálogo', async () => {
    server.use(
      http.post('/api/v1/groups/g-1/exit-requests/r-1/approve', () =>
        problem(409, 'Este pedido de saída já foi decidido.', '/problems/exit-request-not-pending'),
      ),
    );
    renderList();

    await userEvent.click(screen.getByRole('button', { name: 'Aprovar' }));
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Aprovar' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este pedido de saída já foi decidido.',
    );
  });

  it('pedido de um dependente mostra de quem ele é (feature 004)', () => {
    renderList(
      openGroup({
        ...WITH_REQUEST,
        pendingExitRequests: [
          {
            id: 'r-2',
            userId: 'd-1',
            name: 'Lucas',
            requestedAt: '2026-12-02T15:00:00Z',
            guardianName: 'Bruno',
          },
        ],
      }),
    );

    const list = screen.getByRole('list', { name: 'Pedidos de saída' });
    expect(within(list).getByText(/dependente de Bruno/)).toBeInTheDocument();
    expect(within(list).getByText('Lucas', { exact: false })).toBeInTheDocument();
  });
});
