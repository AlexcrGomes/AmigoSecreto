import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeEventSource } from '../test/eventSource';
import { BRUNO_ID, mockGroup, openGroup } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { setMediaMatcher } from '../test/media';
import { server } from '../test/server';
import { GroupPage } from './GroupPage';

// Desenho largo (≥ 768px): as três partes do grupo ficam visíveis juntas (feature 005, R59)
beforeEach(() => setMediaMatcher((query) => query.includes('min-width: 768px')));

const PARTICIPANTS = [
  { userId: ANA.id, name: 'Ana', organizer: true, confirmed: true },
  { userId: BRUNO_ID, name: 'Bruno', organizer: false, confirmed: true },
  { userId: 'c-3', name: 'Carla', organizer: false, confirmed: true },
];

const DRAWN = openGroup({
  status: 'DRAWN',
  drawnAt: '2026-12-01T15:00:00Z',
  participants: PARTICIPANTS,
  participantCount: 3,
  confirmedCount: 3,
  drawBlockedReason: null,
});

const FINISHED = openGroup({
  ...DRAWN,
  status: 'FINISHED',
  finishedAt: '2026-12-20T15:00:00Z',
});

function renderGroup() {
  mockSession(ANA);
  return renderWithProviders(
    <Routes>
      <Route path="/grupos/:id" element={<GroupPage />} />
    </Routes>,
    { route: '/grupos/g-1' },
  );
}

describe('GroupPage — status FINISHED', () => {
  it('mostra "Finalizado" com a data, os detalhes e o cartão do amigo secreto', async () => {
    mockGroup(FINISHED);
    renderGroup();

    expect(await screen.findByText('Finalizado')).toBeInTheDocument();
    expect(screen.getByText('Finalizado em 20/12/2026')).toBeInTheDocument();
    expect(screen.getByText(/Casa da Ana/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Seu amigo secreto' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revelar' })).toBeInTheDocument();
  });

  it('não tem nenhum botão de alteração (FR-018)', async () => {
    mockGroup(FINISHED);
    renderGroup();
    await screen.findByText('Finalizado');

    expect(screen.queryByText('K7PQ-M2XA')).not.toBeInTheDocument();
    for (const name of [
      'Editar detalhes',
      'Informar detalhes',
      'Realizar sorteio',
      'Confirmar participação',
      'Não vou participar',
      'Sair do grupo',
      'Remover',
      'Copiar',
      'Compartilhar',
    ]) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  it('a lista mostra só os nomes', async () => {
    mockGroup(FINISHED);
    renderGroup();

    const list = await screen.findByRole('list', { name: 'Participantes' });
    expect(
      within(list)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['Ana (você)Organizador', 'Bruno', 'Carla']);
    expect(screen.queryByText(/confirmados/)).not.toBeInTheDocument();
  });

  it('o participante ainda revela o próprio amigo secreto (FR-019)', async () => {
    mockGroup(openGroup({ ...FINISHED, organizer: false }));
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () => HttpResponse.json({ name: 'Carla' })),
    );
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Revelar' }));

    expect(await screen.findByRole('button', { name: 'Ocultar' })).toBeInTheDocument();
    const card = screen.getByRole('heading', { name: 'Seu amigo secreto' }).parentElement;
    expect(within(card as HTMLElement).getByText('Carla')).toBeInTheDocument();
  });

  it('o sinal group-finished passa a tela para somente leitura, com o aviso', async () => {
    mockGroup(DRAWN, FINISHED);
    renderGroup();
    expect(await screen.findByText('Sorteado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Editar detalhes' })).toBeInTheDocument();

    act(() =>
      FakeEventSource.latest().emit('group-finished', { status: 'FINISHED', participantCount: 3 }),
    );

    const notice = await screen.findByText('O evento aconteceu. Este grupo foi finalizado.');
    expect(notice.closest('[role="status"]')?.querySelector('svg')).not.toBeNull();
    expect(await screen.findByText('Finalizado')).toHaveClass('rounded-control');
    expect(screen.queryByRole('button', { name: 'Editar detalhes' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revelar' })).toBeInTheDocument();
  });

  it('com o grupo aberto e a data vencida, o organizador é avisado para atualizar a data', async () => {
    mockGroup(
      openGroup({
        participants: PARTICIPANTS,
        participantCount: 3,
        confirmedCount: 3,
        eventDatePassed: true,
        drawBlockedReason: 'event-date-passed',
      }),
    );
    renderGroup();

    expect(
      await screen.findByText('A data do evento já passou. Atualize a data para sortear.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Realizar sorteio' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Editar detalhes' })).toBeEnabled();
    expect(screen.queryByText('Finalizado')).not.toBeInTheDocument();
  });
});
