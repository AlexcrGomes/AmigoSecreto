import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { openGroup, problem } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { JoinGroupPage } from './JoinGroupPage';

function renderJoin(route = '/entrar-no-grupo') {
  mockSession(ANA);
  return renderWithProviders(
    <Routes>
      <Route path="/entrar-no-grupo" element={<JoinGroupPage />} />
      <Route path="/grupos/:id" element={<h1>Tela do grupo</h1>} />
    </Routes>,
    { route },
  );
}

async function submit(code: string) {
  const input = await screen.findByLabelText('Código do grupo');
  await userEvent.clear(input);
  await userEvent.type(input, code);
  await userEvent.click(screen.getByRole('button', { name: 'Entrar no grupo' }));
}

describe('JoinGroupPage', () => {
  it('o formulário fica num cartão de largura limitada (feature 005)', async () => {
    renderJoin();

    const heading = await screen.findByRole('heading', { level: 1, name: 'Entrar com código' });
    expect(heading.closest('.max-w-xl')).not.toBeNull();
  });

  it('campo com máscara XXXX-XXXX, maiúsculas e sem autocorreção', async () => {
    renderJoin();

    const input = await screen.findByLabelText('Código do grupo');
    await userEvent.type(input, 'k7pqm2xa');

    expect(input).toHaveValue('K7PQ-M2XA');
    expect(input).toHaveAttribute('autocapitalize', 'characters');
    expect(input).toHaveAttribute('autocorrect', 'off');
    expect(input).toHaveAttribute('spellcheck', 'false');
    expect(input).toHaveAttribute('autocomplete', 'off');
  });

  it('?codigo= preenche o campo', async () => {
    renderJoin('/entrar-no-grupo?codigo=k7pq-m2xa');

    expect(await screen.findByLabelText('Código do grupo')).toHaveValue('K7PQ-M2XA');
  });

  it('código mal formado nem chega ao servidor', async () => {
    renderJoin();

    await submit('K7PQ');

    expect(await screen.findByText('Código inválido.')).toBeInTheDocument();
  });

  it('sucesso navega para o grupo', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/groups/join', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(openGroup({ id: 'g-9' }));
      }),
    );
    renderJoin();

    await submit('k7pq m2xa');

    expect(await screen.findByRole('heading', { name: 'Tela do grupo' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/grupos/g-9');
    expect(body).toEqual({ code: 'K7PQ-M2XA' });
  });

  it.each([
    [404, undefined, 'Código inválido.'],
    [409, '/problems/group-already-drawn', 'O sorteio deste grupo já foi realizado.'],
    [409, '/problems/group-full', 'Este grupo está cheio.'],
    [429, '/problems/rate-limited', 'Muitas tentativas. Tente novamente em alguns minutos.'],
  ])('erro %s %s mostra "%s"', async (status, type, message) => {
    server.use(
      http.post('/api/v1/groups/join', () => problem(status, 'detalhe do servidor', type)),
    );
    renderJoin();

    await submit('K7PQM2XA');

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByLabelText('Código do grupo')).toHaveAttribute('aria-invalid', 'true');
  });
});
