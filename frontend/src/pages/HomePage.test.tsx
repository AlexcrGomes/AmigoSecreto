import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { GroupSummary } from '../api/groups';
import { problem } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { HomePage } from './HomePage';

function renderHome(groups: GroupSummary[] | 'erro') {
  mockSession(ANA);
  server.use(
    http.get('/api/v1/groups', () =>
      groups === 'erro' ? problem(500, 'Ocorreu um erro inesperado.') : HttpResponse.json(groups),
    ),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/grupos/novo" element={<h1>Criar grupo</h1>} />
      <Route path="/entrar-no-grupo" element={<h1>Entrar com código</h1>} />
      <Route path="/grupos/:id" element={<h1>Tela do grupo</h1>} />
    </Routes>,
  );
}

describe('HomePage', () => {
  it('mostra a data do evento de cada grupo', async () => {
    renderHome([
      {
        id: 'g-1',
        name: 'Natal 2026',
        status: 'OPEN',
        participantCount: 4,
        organizer: true,
        eventDate: '2026-12-19',
        eventTime: '19:00',
      },
      {
        id: 'g-2',
        name: 'Firma',
        status: 'OPEN',
        participantCount: 3,
        organizer: false,
        eventDate: '2026-12-19',
        eventTime: null,
      },
      {
        id: 'g-3',
        name: 'Antigo',
        status: 'DRAWN',
        participantCount: 5,
        organizer: false,
        eventDate: null,
        eventTime: null,
      },
    ]);

    const natal = await screen.findByRole('link', { name: /Natal 2026/ });
    expect(within(natal).getByText('sábado, 19 de dezembro de 2026, às 19:00')).toBeInTheDocument();
    const firma = screen.getByRole('link', { name: /Firma/ });
    expect(within(firma).getByText('sábado, 19 de dezembro de 2026')).toBeInTheDocument();
    const old = screen.getByRole('link', { name: /Antigo/ });
    expect(within(old).getByText('Data do evento a definir')).toBeInTheDocument();
  });

  it('lista os grupos com contagem e selos', async () => {
    renderHome([
      {
        id: 'g-1',
        name: 'Natal 2026',
        status: 'OPEN',
        participantCount: 4,
        organizer: true,
        eventDate: null,
        eventTime: null,
      },
      {
        id: 'g-2',
        name: 'Firma',
        status: 'DRAWN',
        participantCount: 1,
        organizer: false,
        eventDate: null,
        eventTime: null,
      },
    ]);

    const natal = await screen.findByRole('link', { name: /Natal 2026/ });
    expect(within(natal).getByText('4 participantes')).toBeInTheDocument();
    expect(within(natal).getByText('Aguardando sorteio')).toBeInTheDocument();
    expect(within(natal).getByText('Organizador')).toBeInTheDocument();

    const firma = screen.getByRole('link', { name: /Firma/ });
    expect(within(firma).getByText('1 participante')).toBeInTheDocument();
    expect(within(firma).getByText('Sorteado')).toBeInTheDocument();
    expect(within(firma).queryByText('Organizador')).not.toBeInTheDocument();

    await userEvent.click(natal);
    expect(screen.getByTestId('location')).toHaveTextContent('/grupos/g-1');
  });

  it('estado vazio amigável com as duas ações', async () => {
    renderHome([]);

    expect(
      await screen.findByText('Você ainda não participa de nenhum grupo.'),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Criar grupo' }));
    expect(screen.getByRole('heading', { name: 'Criar grupo' })).toBeInTheDocument();
  });

  it('botão "Entrar com código" leva à tela de entrar', async () => {
    renderHome([]);

    await userEvent.click(await screen.findByRole('link', { name: 'Entrar com código' }));

    expect(screen.getByRole('heading', { name: 'Entrar com código' })).toBeInTheDocument();
  });

  it('erro mostra a mensagem e permite tentar de novo', async () => {
    renderHome('erro');

    expect(await screen.findByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado.');
    server.use(http.get('/api/v1/groups', () => HttpResponse.json([])));
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));
    expect(
      await screen.findByText('Você ainda não participa de nenhum grupo.'),
    ).toBeInTheDocument();
  });

  it('separa os grupos em andamento dos finalizados, com o selo "Finalizado" (FR-020)', async () => {
    renderHome([
      {
        id: 'g-1',
        name: 'Natal 2026',
        status: 'OPEN',
        participantCount: 4,
        organizer: true,
        eventDate: '2026-12-19',
        eventTime: '19:00',
      },
      {
        id: 'g-2',
        name: 'Natal 2025',
        status: 'FINISHED',
        participantCount: 6,
        organizer: false,
        eventDate: '2025-12-20',
        eventTime: null,
      },
      {
        id: 'g-3',
        name: 'Firma',
        status: 'DRAWN',
        participantCount: 3,
        organizer: false,
        eventDate: '2026-12-18',
        eventTime: null,
      },
    ]);

    const active = await screen.findByRole('region', { name: 'Em andamento' });
    const finished = screen.getByRole('region', { name: 'Finalizados' });
    expect(
      within(active)
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/grupos/g-1', '/grupos/g-3']);
    const old = within(finished).getByRole('link', { name: /Natal 2025/ });
    expect(within(old).getByText('Finalizado')).toBeInTheDocument();
    expect(within(active).queryByText('Finalizado')).not.toBeInTheDocument();
  });

  it('sem grupos finalizados, não mostra a seção', async () => {
    renderHome([
      {
        id: 'g-1',
        name: 'Natal 2026',
        status: 'OPEN',
        participantCount: 4,
        organizer: true,
        eventDate: '2026-12-19',
        eventTime: '19:00',
      },
    ]);

    expect(await screen.findByRole('region', { name: 'Em andamento' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Finalizados' })).not.toBeInTheDocument();
  });

  it('só com grupos finalizados, não mostra "Em andamento"', async () => {
    renderHome([
      {
        id: 'g-2',
        name: 'Natal 2025',
        status: 'FINISHED',
        participantCount: 6,
        organizer: false,
        eventDate: '2025-12-20',
        eventTime: null,
      },
    ]);

    expect(await screen.findByRole('region', { name: 'Finalizados' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Em andamento' })).not.toBeInTheDocument();
  });

  it('mostra "Você saiu do grupo X" para a saída aprovada e marca como visto', async () => {
    let seen = false;
    server.use(
      http.get('/api/v1/exit-request-notices', () =>
        HttpResponse.json(
          seen
            ? []
            : [
                {
                  id: 'r-1',
                  groupId: 'g-9',
                  groupName: 'Firma',
                  status: 'APPROVED',
                  decidedAt: '2026-12-03T12:00:00Z',
                },
                {
                  id: 'r-2',
                  groupId: 'g-8',
                  groupName: 'Família',
                  status: 'REJECTED',
                  decidedAt: '2026-12-03T12:00:00Z',
                },
              ],
        ),
      ),
      http.post('/api/v1/exit-request-notices/r-1/ack', () => {
        seen = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderHome([]);

    expect(await screen.findByText(/Você saiu do grupo/)).toHaveTextContent(
      'Você saiu do grupo Firma. O organizador aprovou seu pedido de saída.',
    );
    // A recusa aparece na tela do grupo, não aqui
    expect(screen.queryByText(/Família/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Entendi' }));

    await vi.waitFor(() =>
      expect(screen.queryByText(/Você saiu do grupo/)).not.toBeInTheDocument(),
    );
  });

  it('a saída aprovada de um dependente fala do dependente (feature 004)', async () => {
    server.use(
      http.get('/api/v1/exit-request-notices', () =>
        HttpResponse.json([
          {
            id: 'r-3',
            groupId: 'g-9',
            groupName: 'Firma',
            status: 'APPROVED',
            decidedAt: '2026-12-03T12:00:00Z',
            subjectName: 'Lucas',
          },
        ]),
      ),
    );
    renderHome([]);

    expect(await screen.findByText(/Lucas saiu do grupo/)).toHaveTextContent(
      'Lucas saiu do grupo Firma. O organizador aprovou o pedido de saída.',
    );
    expect(screen.queryByText(/Você saiu do grupo/)).not.toBeInTheDocument();
  });
});
