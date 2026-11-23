import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { FakeEventSource } from '../test/eventSource';
import { BRUNO_ID, mockGroup, openGroup } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { GroupPage } from './GroupPage';

const READY = openGroup({
  participantCount: 3,
  canDraw: true,
  participants: [
    ...openGroup().participants,
    { userId: 'c-3', name: 'Carla', organizer: false, confirmed: true },
  ],
});
const DRAWN = {
  ...READY,
  status: 'DRAWN' as const,
  canDraw: false,
  drawnAt: '2026-12-20T15:00:00Z',
};

function renderGroup() {
  mockSession(ANA);
  return renderWithProviders(
    <Routes>
      <Route path="/grupos/:id" element={<GroupPage />} />
      <Route path="/" element={<h1>Meus grupos</h1>} />
    </Routes>,
    { route: '/grupos/g-1' },
  );
}

describe('GroupPage — status DRAWN', () => {
  it('mostra "Sorteado" com a data e o cartão do amigo secreto', async () => {
    mockGroup(DRAWN);
    renderGroup();

    expect(await screen.findByText('Sorteado')).toBeInTheDocument();
    expect(screen.getByText(/Sorteio realizado em 20\/12\/2026/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Seu amigo secreto' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revelar' })).toBeInTheDocument();
  });

  it('não mostra código, sortear, entrar, sair nem remover', async () => {
    mockGroup(DRAWN);
    renderGroup();
    await screen.findByText('Sorteado');

    expect(screen.queryByText('K7PQ-M2XA')).not.toBeInTheDocument();
    for (const name of ['Realizar sorteio', 'Sair do grupo', 'Remover', 'Copiar', 'Compartilhar']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  it('a lista mostra só os nomes, sem indicar quem tirou quem', async () => {
    mockGroup(DRAWN);
    renderGroup();

    const list = await screen.findByRole('list', { name: 'Participantes' });
    const items = within(list).getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      'Ana (você)Organizador',
      'Bruno',
      'Carla',
    ]);
  });

  it('ao receber group-drawn estando OPEN, passa para DRAWN com o aviso', async () => {
    mockGroup(READY, DRAWN);
    renderGroup();
    expect(await screen.findByText('Aguardando sorteio')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Realizar sorteio' })).toBeEnabled();

    act(() => FakeEventSource.latest().emit('group-drawn', { status: 'DRAWN' }));

    expect(
      await screen.findByText(
        'O sorteio foi realizado! Toque em Revelar para ver quem você tirou.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Sorteado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revelar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Realizar sorteio' })).not.toBeInTheDocument();
  });

  // ---- saída depois do sorteio (US4 da 002) ----

  /** Ana é só participante; Bruno organiza. */
  const AS_GUEST = openGroup({
    ...DRAWN,
    organizer: false,
    participants: [
      { userId: BRUNO_ID, name: 'Bruno', organizer: true, confirmed: true },
      { userId: ANA.id, name: 'Ana', organizer: false, confirmed: true },
      { userId: 'c-3', name: 'Carla', organizer: false, confirmed: true },
    ],
  });
  const UNDONE = openGroup({
    ...READY,
    drawUndone: true,
    participantCount: 2,
  });

  it('o participante vê "Preciso sair do grupo"; o organizador não', async () => {
    mockGroup(AS_GUEST);
    renderGroup();

    expect(
      await screen.findByRole('button', { name: 'Preciso sair do grupo' }),
    ).toBeInTheDocument();
  });

  it('o organizador vê os pedidos de saída, e o participante não', async () => {
    mockGroup(
      openGroup({
        ...DRAWN,
        pendingExitRequests: [
          { id: 'r-1', userId: BRUNO_ID, name: 'Bruno', requestedAt: '2026-12-02T15:00:00Z' },
        ],
      }),
    );
    renderGroup();

    expect(await screen.findByRole('heading', { name: 'Pedidos de saída' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Preciso sair do grupo' })).not.toBeInTheDocument();
  });

  it('draw-undone apaga o nome revelado e avisa que o sorteio foi desfeito', async () => {
    mockGroup(AS_GUEST);
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () => HttpResponse.json({ name: 'Carla' })),
    );
    renderGroup();
    await userEvent.click(await screen.findByRole('button', { name: 'Revelar' }));
    expect(await screen.findByRole('button', { name: 'Ocultar' })).toBeInTheDocument();

    // A próxima consulta do grupo já vem desfeita
    mockGroup(openGroup({ ...UNDONE, organizer: false }));
    act(() =>
      FakeEventSource.latest().emit('draw-undone', { status: 'OPEN', participantCount: 2 }),
    );

    expect(screen.queryByRole('button', { name: 'Ocultar' })).not.toBeInTheDocument();
    expect(
      await screen.findByText(
        'Uma pessoa saiu do grupo e o sorteio foi desfeito. O organizador fará um novo sorteio.',
      ),
    ).toBeInTheDocument();
    expect(await screen.findByText('Aguardando sorteio')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Seu amigo secreto' })).not.toBeInTheDocument();
  });

  it('quem abre o grupo depois vê o aviso fixo de sorteio desfeito', async () => {
    mockGroup(UNDONE);
    renderGroup();

    expect(
      await screen.findByText(
        'Uma pessoa saiu do grupo e o sorteio foi desfeito. O organizador fará um novo sorteio.',
      ),
    ).toBeInTheDocument();
  });

  it('saída aprovada ao vivo volta para a tela inicial com a mensagem', async () => {
    let acked = false;
    server.use(
      http.get('/api/v1/exit-request-notices', () =>
        HttpResponse.json([
          {
            id: 'r-9',
            groupId: 'g-1',
            groupName: 'Natal 2026',
            status: 'APPROVED',
            decidedAt: '2026-12-03T12:00:00Z',
          },
        ]),
      ),
      http.post('/api/v1/exit-request-notices/r-9/ack', () => {
        acked = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    mockGroup(AS_GUEST);
    renderGroup();
    await screen.findByText('Sorteado');

    act(() =>
      FakeEventSource.latest().emit('membership-revoked', {
        groupId: 'g-1',
        reason: 'exit-approved',
      }),
    );

    expect(
      await screen.findByText('Seu pedido de saída foi aprovado. Você saiu do grupo.'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/);
    await vi.waitFor(() => expect(acked).toBe(true));
  });

  it('a recusa guardada aparece na tela do grupo até "Entendi"', async () => {
    let seen = false;
    server.use(
      http.get('/api/v1/exit-request-notices', () =>
        HttpResponse.json(
          seen
            ? []
            : [
                {
                  id: 'r-2',
                  groupId: 'g-1',
                  groupName: 'Natal 2026',
                  status: 'REJECTED',
                  decidedAt: '2026-12-03T12:00:00Z',
                },
              ],
        ),
      ),
      http.post('/api/v1/exit-request-notices/r-2/ack', () => {
        seen = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    mockGroup(AS_GUEST);
    renderGroup();

    expect(
      await screen.findByText('O organizador recusou seu pedido de saída.'),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Entendi' }));

    await vi.waitFor(() =>
      expect(
        screen.queryByText('O organizador recusou seu pedido de saída.'),
      ).not.toBeInTheDocument(),
    );
  });
});
