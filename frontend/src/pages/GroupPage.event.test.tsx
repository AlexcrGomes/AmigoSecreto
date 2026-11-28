import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeEventSource } from '../test/eventSource';
import { mockGroup, openGroup } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { setMediaMatcher } from '../test/media';
import { server } from '../test/server';
import { GroupPage } from './GroupPage';

// Desenho largo (≥ 768px): as três partes do grupo ficam visíveis juntas (feature 005, R59)
beforeEach(() => setMediaMatcher((query) => query.includes('min-width: 768px')));

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

describe('GroupPage — detalhes do evento (US1)', () => {
  it('todos veem a data, o local e a descrição', async () => {
    mockGroup(
      openGroup({
        organizer: false,
        eventDetails: {
          description: 'Traga um prato',
          eventDate: '2026-12-19',
          eventTime: '19:00',
          location: 'Casa da Ana',
        },
      }),
    );
    renderGroup();

    const card = (await screen.findByRole('heading', { name: 'Detalhes do evento' })).closest(
      'section',
    ) as HTMLElement;
    expect(within(card).getByText('sábado, 19 de dezembro de 2026, às 19:00')).toBeInTheDocument();
    expect(within(card).getByText('Casa da Ana')).toBeInTheDocument();
    expect(within(card).getByText('Traga um prato')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar detalhes' })).not.toBeInTheDocument();
  });

  it('o organizador edita os detalhes num modal e a tela mostra o resultado', async () => {
    let body: unknown;
    mockGroup(openGroup());
    server.use(
      http.put('/api/v1/groups/g-1/event-details', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          openGroup({
            detailsVersion: 2,
            eventDetails: {
              description: null,
              eventDate: '2026-12-19',
              eventTime: '19:00',
              location: 'Salão de festas',
            },
          }),
        );
      }),
    );
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Editar detalhes' }));
    const dialog = screen.getByRole('dialog', { name: 'Editar detalhes do evento' });
    const location = within(dialog).getByLabelText('Local do evento');
    expect(location).toHaveValue('Casa da Ana');
    await userEvent.clear(location);
    await userEvent.type(location, 'Salão de festas');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Detalhes do evento atualizados.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('Salão de festas')).toBeInTheDocument();
    expect(body).toEqual({
      description: null,
      eventDate: '2026-12-19',
      eventTime: '19:00',
      location: 'Salão de festas',
      giftValue: null,
    });
  });

  it('cancelar fecha o modal sem salvar', async () => {
    mockGroup(openGroup());
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Editar detalhes' }));
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }),
    );

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('organizador de grupo antigo é avisado e informa os detalhes', async () => {
    mockGroup(openGroup({ eventDetails: null, eventDetailsMissing: true }));
    renderGroup();

    expect(await screen.findByText('Informe a data e o local do evento')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Informar detalhes' }));

    const dialog = screen.getByRole('dialog', { name: 'Detalhes do evento' });
    expect(within(dialog).getByLabelText('Local do evento')).toHaveValue('');
  });

  it('participante de grupo antigo vê que a data ainda vai ser definida', async () => {
    mockGroup(openGroup({ organizer: false, eventDetails: null, eventDetailsMissing: true }));
    renderGroup();

    expect(
      await screen.findByText('O organizador ainda vai informar a data e o local do evento.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Informar detalhes' })).not.toBeInTheDocument();
  });

  it('a mudança do organizador aparece ao vivo (group-updated)', async () => {
    mockGroup(
      openGroup({ organizer: false }),
      openGroup({
        organizer: false,
        eventDetails: {
          description: null,
          eventDate: '2026-12-19',
          eventTime: null,
          location: 'Salão de festas',
        },
      }),
    );
    renderGroup();
    expect(await screen.findByText('Casa da Ana')).toBeInTheDocument();

    act(() =>
      FakeEventSource.latest().emit('group-updated', {
        status: 'OPEN',
        participantCount: 2,
        confirmationsReset: false,
      }),
    );

    expect(await screen.findByText('Salão de festas')).toBeInTheDocument();
    expect(screen.getByText('sábado, 19 de dezembro de 2026')).toBeInTheDocument();
  });

  it('grupo finalizado não mostra "Editar detalhes"', async () => {
    mockGroup(openGroup({ status: 'FINISHED', drawnAt: '2026-12-01T12:00:00Z' }));
    renderGroup();

    expect(await screen.findByRole('heading', { name: 'Detalhes do evento' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar detalhes' })).not.toBeInTheDocument();
  });
});
