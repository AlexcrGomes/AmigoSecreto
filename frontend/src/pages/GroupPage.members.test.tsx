import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { FakeEventSource } from '../test/eventSource';
import { BRUNO_ID, mockGroup, openGroup, problem } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { GroupPage } from './GroupPage';

const CARLA = { userId: 'c-3', name: 'Carla', organizer: false, confirmed: true };
const ORGANIZER_VIEW = openGroup({
  participants: [...openGroup().participants, CARLA],
  participantCount: 3,
  canDraw: true,
});
// Ana como participante comum (o organizador é o Bruno)
const MEMBER_VIEW = openGroup({
  organizer: false,
  participants: [
    { userId: BRUNO_ID, name: 'Bruno', organizer: true, confirmed: true },
    { userId: ANA.id, name: 'Ana', organizer: false, confirmed: true },
  ],
});

function renderGroup() {
  mockSession(ANA);
  server.use(http.get('/api/v1/groups', () => HttpResponse.json([])));
  return renderWithProviders(
    <Routes>
      <Route path="/grupos/:id" element={<GroupPage />} />
      <Route path="/" element={<h1>Meus grupos</h1>} />
    </Routes>,
    { route: '/grupos/g-1' },
  );
}

describe('GroupPage — sair e remover (US4)', () => {
  it('o organizador vê "Remover" em cada participante, exceto em si mesmo', async () => {
    mockGroup(ORGANIZER_VIEW);
    renderGroup();

    const list = await screen.findByRole('list', { name: 'Participantes' });
    const [ana, bruno, carla] = within(list).getAllByRole('listitem');
    expect(within(ana as HTMLElement).queryByRole('button')).not.toBeInTheDocument();
    expect(
      within(bruno as HTMLElement).getByRole('button', { name: 'Remover Bruno' }),
    ).toBeInTheDocument();
    expect(
      within(carla as HTMLElement).getByRole('button', { name: 'Remover Carla' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sair do grupo' })).not.toBeInTheDocument();
  });

  it('remover pede confirmação e chama a API', async () => {
    let removed: string | undefined;
    mockGroup(ORGANIZER_VIEW, openGroup());
    server.use(
      http.delete('/api/v1/groups/g-1/participants/:userId', ({ params }) => {
        removed = String(params.userId);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Remover Carla' }));
    const dialog = screen.getByRole('dialog', { name: 'Remover participante?' });
    expect(dialog).toHaveTextContent('Carla');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remover' }));

    expect(await screen.findByText('Carla não participa mais do grupo.')).toBeInTheDocument();
    expect(removed).toBe('c-3');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('cancelar a remoção não chama a API', async () => {
    mockGroup(ORGANIZER_VIEW);
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Remover Carla' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('erro ao remover aparece no diálogo', async () => {
    mockGroup(ORGANIZER_VIEW);
    server.use(
      http.delete('/api/v1/groups/g-1/participants/:userId', () =>
        problem(409, 'O sorteio deste grupo já foi realizado.', '/problems/group-already-drawn'),
      ),
    );
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Remover Carla' }));
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Remover' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O sorteio deste grupo já foi realizado.',
    );
  });

  it('o participante comum vê "Sair do grupo" com confirmação e volta para /', async () => {
    let left = false;
    mockGroup(MEMBER_VIEW);
    server.use(
      http.delete('/api/v1/groups/g-1/participants/me', () => {
        left = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderGroup();

    expect(await screen.findByRole('button', { name: 'Sair do grupo' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Remover/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Sair do grupo' }));
    const dialog = screen.getByRole('dialog', { name: 'Sair do grupo?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sair' }));

    expect(await screen.findByRole('heading', { name: 'Meus grupos' })).toBeInTheDocument();
    expect(left).toBe(true);
    expect(screen.getByText('Você saiu do grupo.')).toBeInTheDocument();
  });

  it('membership-revoked mostra o aviso e volta para /', async () => {
    mockGroup(MEMBER_VIEW);
    renderGroup();
    await screen.findByRole('button', { name: 'Sair do grupo' });
    const source = FakeEventSource.latest();

    act(() => source.emit('membership-revoked', { groupId: 'g-1' }));

    expect(await screen.findByRole('heading', { name: 'Meus grupos' })).toBeInTheDocument();
    expect(screen.getByText('Você foi removido deste grupo')).toBeInTheDocument();
    expect(source.closed).toBe(true);
  });

  it('nada disso aparece em DRAWN', async () => {
    mockGroup({
      ...ORGANIZER_VIEW,
      status: 'DRAWN',
      drawnAt: '2026-12-20T15:00:00Z',
      canDraw: false,
    });
    renderGroup();
    await screen.findByText('Sorteado');

    expect(screen.queryByRole('button', { name: /Remover/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sair do grupo' })).not.toBeInTheDocument();
  });

  it('em DRAWN o participante comum também não vê "Sair do grupo"', async () => {
    mockGroup({ ...MEMBER_VIEW, status: 'DRAWN', drawnAt: '2026-12-20T15:00:00Z' });
    renderGroup();
    await screen.findByText('Sorteado');

    expect(screen.queryByRole('button', { name: 'Sair do grupo' })).not.toBeInTheDocument();
  });

  it('mostra de quem é cada dependente (feature 004)', async () => {
    mockGroup(
      openGroup({
        participants: [
          ...ORGANIZER_VIEW.participants,
          {
            userId: 'd-1',
            name: 'Lucas',
            organizer: false,
            confirmed: true,
            dependent: true,
            guardianName: 'Carla',
          },
        ],
      }),
    );
    renderGroup();

    const list = await screen.findByRole('list', { name: 'Participantes' });
    expect(within(list).getByText(/dependente de Carla/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Meus dependentes' })).toBeInTheDocument();
  });

  it('remover um responsável avisa que os dependentes saem junto', async () => {
    mockGroup(
      openGroup({
        participants: [
          ...ORGANIZER_VIEW.participants,
          {
            userId: 'd-1',
            name: 'Lucas',
            organizer: false,
            confirmed: true,
            dependent: true,
            guardianName: 'Carla',
          },
          {
            userId: 'd-2',
            name: 'Bia',
            organizer: false,
            confirmed: true,
            dependent: true,
            guardianName: 'Carla',
          },
        ],
      }),
    );
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Remover Carla' }));
    expect(screen.getByRole('dialog', { name: 'Remover participante?' })).toHaveTextContent(
      'Os dependentes de Carla (Lucas, Bia) também saem do grupo.',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    await userEvent.click(screen.getByRole('button', { name: 'Remover Lucas' }));
    const dialog = screen.getByRole('dialog', { name: 'Remover participante?' });
    expect(dialog).toHaveTextContent('Lucas sairá do grupo.');
    expect(dialog).not.toHaveTextContent('será avisado');
  });
});
