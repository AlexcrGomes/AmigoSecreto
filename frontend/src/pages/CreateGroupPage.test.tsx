import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { todayInBrasilia } from '../lib/eventDate';
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

/** Daqui a 30 dias, em Brasília. */
function inAMonth(): string {
  const [y = 0, m = 1, d = 1] = todayInBrasilia().split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + 30)).toISOString().slice(0, 10);
}

async function fillValidDetails() {
  await userEvent.type(screen.getByLabelText('Data do evento'), inAMonth());
  await userEvent.type(screen.getByLabelText('Local do evento'), 'Casa da Ana');
}

describe('CreateGroupPage', () => {
  it('nome, data e local são obrigatórios', async () => {
    renderCreate();

    await userEvent.click(await screen.findByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByText('Dê um nome ao grupo (até 60 caracteres).')).toBeInTheDocument();
    expect(screen.getByText('Informe a data do evento.')).toBeInTheDocument();
    expect(screen.getByText('Informe o local do evento (até 120 caracteres).')).toBeInTheDocument();
  });

  it('nome com mais de 60 caracteres é recusado', async () => {
    renderCreate();

    await userEvent.type(await screen.findByLabelText('Nome do grupo'), 'x'.repeat(61));
    await fillValidDetails();
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByText('Dê um nome ao grupo (até 60 caracteres).')).toBeInTheDocument();
  });

  it('sucesso cria com os detalhes do evento e navega para o grupo', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/groups', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(openGroup({ id: 'g-5' }), { status: 201 });
      }),
    );
    renderCreate();

    await userEvent.type(await screen.findByLabelText('Nome do grupo'), '  Natal 2026 ');
    await userEvent.type(screen.getByLabelText('Descrição (opcional)'), 'Traga um prato');
    await fillValidDetails();
    await userEvent.type(screen.getByLabelText('Horário (opcional)'), '19:30');
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByRole('heading', { name: 'Tela do grupo' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/grupos/g-5');
    expect(body).toEqual({
      name: 'Natal 2026',
      description: 'Traga um prato',
      eventDate: inAMonth(),
      eventTime: '19:30',
      location: 'Casa da Ana',
    });
  });

  it('erro do servidor aparece no formulário, com o erro do campo', async () => {
    server.use(
      http.post('/api/v1/groups', () =>
        HttpResponse.json(
          {
            status: 400,
            type: '/problems/validation-error',
            detail: 'Verifique os campos destacados.',
            errors: [
              { field: 'location', message: 'Informe o local do evento (até 120 caracteres).' },
            ],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderCreate();

    await userEvent.type(await screen.findByLabelText('Nome do grupo'), 'Natal');
    await fillValidDetails();
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Verifique os campos destacados.');
    expect(screen.getByText('Informe o local do evento (até 120 caracteres).')).toBeInTheDocument();
  });

  it('erro inesperado aparece no formulário', async () => {
    server.use(http.post('/api/v1/groups', () => problem(500, 'Ocorreu um erro inesperado.')));
    renderCreate();

    await userEvent.type(await screen.findByLabelText('Nome do grupo'), 'Natal');
    await fillValidDetails();
    await userEvent.click(screen.getByRole('button', { name: 'Criar grupo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado.');
  });

  it('cancelar volta para Meus grupos', async () => {
    renderCreate();

    await userEvent.click(await screen.findByRole('link', { name: 'Cancelar' }));

    expect(screen.getByRole('heading', { name: 'Meus grupos' })).toBeInTheDocument();
  });
});
