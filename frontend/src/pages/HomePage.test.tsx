import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
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
  it('lista os grupos com contagem e selos', async () => {
    renderHome([
      { id: 'g-1', name: 'Natal 2026', status: 'OPEN', participantCount: 4, organizer: true },
      { id: 'g-2', name: 'Firma', status: 'DRAWN', participantCount: 1, organizer: false },
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
});
