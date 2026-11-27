import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { RegisterPage } from './RegisterPage';

function renderRegister(route = '/cadastro') {
  return renderWithProviders(
    <Routes>
      <Route path="/cadastro" element={<RegisterPage />} />
      <Route path="/" element={<h1>Meus grupos</h1>} />
      <Route path="/entrar" element={<h1>Entrar</h1>} />
      <Route path="/grupos/:id" element={<h1>Grupo</h1>} />
    </Routes>,
    { route },
  );
}

async function fill(name: string, email: string, password: string) {
  if (name) await userEvent.type(await screen.findByLabelText('Nome'), name);
  if (email) await userEvent.type(screen.getByLabelText('E-mail'), email);
  if (password) await userEvent.type(screen.getByLabelText('Senha'), password);
}

describe('RegisterPage', () => {
  it('valida nome, e-mail e senha com as mensagens do data-model', async () => {
    mockSession(null);
    renderRegister();

    await fill('A', 'nao-e-email', 'curta');
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByText('Informe seu nome (2 a 80 caracteres).')).toBeInTheDocument();
    expect(screen.getByText('Informe um e-mail válido.')).toBeInTheDocument();
    expect(screen.getByText('A senha deve ter entre 8 e 64 caracteres.')).toBeInTheDocument();
  });

  it('mostra a regra da senha sempre visível e não tem "esqueci minha senha"', async () => {
    mockSession(null);
    renderRegister();

    const password = await screen.findByLabelText('Senha');
    expect(password).toHaveAccessibleDescription('De 8 a 64 caracteres.');
    expect(password).toHaveAttribute('autocomplete', 'new-password');
    expect(screen.queryByText(/esqueci/i)).not.toBeInTheDocument();
  });

  it('sucesso cria a conta, entra e navega para /', async () => {
    mockSession(null);
    let body: unknown;
    server.use(
      http.post('/api/v1/auth/register', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(ANA, { status: 201 });
      }),
    );
    renderRegister();

    await fill('  Ana  ', 'ana@x.com', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByRole('heading', { name: 'Meus grupos' })).toBeInTheDocument();
    expect(body).toEqual({ name: 'Ana', email: 'ana@x.com', password: 'senha-forte' });
  });

  it('sucesso respeita o "voltar"', async () => {
    mockSession(null);
    server.use(http.post('/api/v1/auth/register', () => HttpResponse.json(ANA, { status: 201 })));
    renderRegister(`/cadastro?voltar=${encodeURIComponent('/grupos/3')}`);

    await fill('Ana', 'ana@x.com', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByRole('heading', { name: 'Grupo' })).toBeInTheDocument();
  });

  it('409 marca o e-mail como já cadastrado', async () => {
    mockSession(null);
    server.use(
      http.post('/api/v1/auth/register', () =>
        HttpResponse.json(
          {
            status: 409,
            type: '/problems/email-already-used',
            detail: 'Este e-mail já está cadastrado.',
          },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderRegister();

    await fill('Ana', 'ana@x.com', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByText('Este e-mail já está cadastrado.')).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('aria-invalid', 'true');
  });

  it('erros de campo do servidor aparecem nos campos', async () => {
    mockSession(null);
    server.use(
      http.post('/api/v1/auth/register', () =>
        HttpResponse.json(
          {
            status: 400,
            type: '/problems/validation-error',
            detail: 'Verifique os campos destacados.',
            errors: [
              {
                field: 'password',
                message:
                  'A senha deve ter no máximo 72 bytes (acentos e emojis ocupam mais de um).',
              },
            ],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderRegister();

    await fill('Ana', 'ana@x.com', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(
      await screen.findByText(
        'A senha deve ter no máximo 72 bytes (acentos e emojis ocupam mais de um).',
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Verifique os campos destacados.');
  });

  it('429 mostra o aviso de muitas tentativas', async () => {
    mockSession(null);
    server.use(
      http.post('/api/v1/auth/register', () =>
        HttpResponse.json(
          { status: 429, detail: 'x' },
          { status: 429, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    renderRegister();

    await fill('Ana', 'ana@x.com', 'senha-forte');
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Muitas tentativas. Tente novamente em alguns minutos.');
    expect(alert.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    // Cartão de vidro do AuthLayout (feature 005)
    expect(
      screen.getByRole('heading', { level: 1, name: 'Criar conta' }).closest('.surface-glass'),
    ).not.toBeNull();
  });

  it('tem link para entrar mantendo o "voltar"', async () => {
    mockSession(null);
    renderRegister(`/cadastro?voltar=${encodeURIComponent('/grupos/3')}`);

    await userEvent.click(await screen.findByRole('link', { name: 'Entrar' }));

    expect(screen.getByTestId('location')).toHaveTextContent(
      `/entrar?voltar=${encodeURIComponent('/grupos/3')}`,
    );
  });
});
