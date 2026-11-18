import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { GuestOnly } from '../auth/RequireAuth';
import { server } from '../test/server';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { LoginPage } from './LoginPage';

function problem(status: number, detail: string, headers: Record<string, string> = {}) {
  return HttpResponse.json(
    { status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json', ...headers } },
  );
}

function renderLogin(route = '/entrar') {
  return renderWithProviders(
    <Routes>
      <Route element={<GuestOnly />}>
        <Route path="/entrar" element={<LoginPage />} />
      </Route>
      <Route path="/" element={<h1>Meus grupos</h1>} />
      <Route path="/grupos/:id" element={<h1>Grupo</h1>} />
      <Route path="/cadastro" element={<h1>Criar conta</h1>} />
    </Routes>,
    { route },
  );
}

async function fill(email: string, password: string) {
  await userEvent.type(await screen.findByLabelText('E-mail'), email);
  await userEvent.type(screen.getByLabelText('Senha'), password);
}

describe('LoginPage', () => {
  it('valida os campos em pt-BR antes de enviar', async () => {
    mockSession(null);
    renderLogin();

    await userEvent.click(await screen.findByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('Informe um e-mail válido.')).toBeInTheDocument();
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('aria-invalid', 'true');
  });

  it('não tem link de "esqueci minha senha" (FR-006) e leva ao cadastro', async () => {
    mockSession(null);
    renderLogin();

    await screen.findByRole('heading', { name: 'Entrar' });
    expect(screen.queryByText(/esqueci/i)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('link', { name: 'Criar conta' }));
    expect(await screen.findByRole('heading', { name: 'Criar conta' })).toBeInTheDocument();
  });

  it('campos com autocomplete e teclado de e-mail', async () => {
    mockSession(null);
    renderLogin();

    const email = await screen.findByLabelText('E-mail');
    expect(email).toHaveAttribute('autocomplete', 'email');
    expect(email).toHaveAttribute('inputmode', 'email');
    expect(screen.getByLabelText('Senha')).toHaveAttribute('autocomplete', 'current-password');
  });

  it('sucesso navega para / e mostra loading durante o envio', async () => {
    mockSession(null);
    let body: unknown;
    server.use(
      http.post('/api/v1/auth/login', async ({ request }) => {
        body = await request.json();
        await delay(50);
        return HttpResponse.json(ANA);
      }),
    );
    renderLogin();

    await fill(' ana@x.com ', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(screen.getByRole('button', { name: /Entrar/ })).toHaveAttribute('aria-busy', 'true');
    expect(await screen.findByRole('heading', { name: 'Meus grupos' })).toBeInTheDocument();
    expect(body).toEqual({ email: 'ana@x.com', password: 'senha-forte' });
  });

  it('sucesso volta para a rota de "voltar"', async () => {
    mockSession(null);
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.json(ANA)));
    renderLogin(`/entrar?voltar=${encodeURIComponent('/grupos/9')}`);

    await fill('ana@x.com', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('heading', { name: 'Grupo' })).toBeInTheDocument();
  });

  it('ignora "voltar" para fora do app (open redirect)', async () => {
    mockSession(null);
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.json(ANA)));
    renderLogin(`/entrar?voltar=${encodeURIComponent('//malicioso.example')}`);

    await fill('ana@x.com', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('heading', { name: 'Meus grupos' })).toBeInTheDocument();
  });

  it('401 mostra a mensagem genérica', async () => {
    mockSession(null);
    server.use(http.post('/api/v1/auth/login', () => problem(401, 'E-mail ou senha inválidos.')));
    renderLogin();

    await fill('ana@x.com', 'errada-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.');
  });

  it('429 mostra o aviso de muitas tentativas', async () => {
    mockSession(null);
    server.use(
      http.post('/api/v1/auth/login', () =>
        problem(429, 'Muitas tentativas.', { 'Retry-After': '600' }),
      ),
    );
    renderLogin();

    await fill('ana@x.com', 'errada-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Muitas tentativas. Tente novamente em alguns minutos.',
    );
  });

  it('falha de rede mostra a mensagem de conexão', async () => {
    mockSession(null);
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.error()));
    renderLogin();

    await fill('ana@x.com', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Sem conexão.');
  });
});
