import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { openGroup, problem } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { CreateGroupPage } from './CreateGroupPage';

function renderCreate() {
  mockSession(ANA);
  return renderWithProviders(
    <Routes>
      <Route path="/grupos/novo" element={<CreateGroupPage />} />
      <Route path="/grupos/:id" element={<h1>Tela do grupo</h1>} />
      <Route path="/" element={<h1>Meus grupos</h1>} />
    </Routes>,
    { route: '/grupos/novo' },
  );
}

describe('CreateGroupPage', () => {
  it('nome é obrigatório', async () => {
    renderCreate();

    await userEvent.click(await screen.findByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByText('Dê um nome ao grupo (até 60 caracteres).')).toBeInTheDocument();
  });

  it('nome com mais de 60 caracteres é recusado', async () => {
    renderCreate();

    await userEvent.type(await screen.findByLabelText('Nome do grupo'), 'x'.repeat(61));
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByText('Dê um nome ao grupo (até 60 caracteres).')).toBeInTheDocument();
  });

  it('sucesso cria e navega para o grupo', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/groups', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(openGroup({ id: 'g-5' }), { status: 201 });
      }),
    );
    renderCreate();

    await userEvent.type(await screen.findByLabelText('Nome do grupo'), '  Natal 2026 ');
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByRole('heading', { name: 'Tela do grupo' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/grupos/g-5');
    expect(body).toEqual({ name: 'Natal 2026' });
  });

  it('erro do servidor aparece no formulário', async () => {
    server.use(http.post('/api/v1/groups', () => problem(500, 'Ocorreu um erro inesperado.')));
    renderCreate();

    await userEvent.type(await screen.findByLabelText('Nome do grupo'), 'Natal');
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado.');
  });

  it('cancelar volta para Meus grupos', async () => {
    renderCreate();

    await userEvent.click(await screen.findByRole('link', { name: 'Cancelar' }));

    expect(screen.getByRole('heading', { name: 'Meus grupos' })).toBeInTheDocument();
  });
});
