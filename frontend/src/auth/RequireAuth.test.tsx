import { act, screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { UNAUTHORIZED_EVENT } from '../api/client';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { GuestOnly, RequireAuth } from './RequireAuth';

function routes() {
  return (
    <Routes>
      <Route element={<GuestOnly />}>
        <Route path="/entrar" element={<h1>Tela de login</h1>} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route path="/" element={<h1>Início</h1>} />
        <Route path="/grupos/:id" element={<h1>Grupo</h1>} />
      </Route>
    </Routes>
  );
}

describe('RequireAuth', () => {
  it('sem usuário, redireciona para /entrar?voltar=<rota>', async () => {
    mockSession(null);

    renderWithProviders(routes(), { route: '/grupos/42?aba=x' });

    expect(await screen.findByRole('heading', { name: 'Tela de login' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent(
      `/entrar?voltar=${encodeURIComponent('/grupos/42?aba=x')}`,
    );
  });

  it('com usuário, renderiza a tela protegida', async () => {
    mockSession(ANA);

    renderWithProviders(routes(), { route: '/grupos/42' });

    expect(await screen.findByRole('heading', { name: 'Grupo' })).toBeInTheDocument();
  });

  it('aceita filhos diretos além de rotas aninhadas', async () => {
    mockSession(ANA);

    renderWithProviders(
      <RequireAuth>
        <p>conteúdo protegido</p>
      </RequireAuth>,
    );

    expect(await screen.findByText('conteúdo protegido')).toBeInTheDocument();
  });

  it('enquanto carrega a sessão, mostra um indicador acessível', () => {
    mockSession(ANA);

    renderWithProviders(routes(), { route: '/' });

    expect(screen.getByRole('status', { name: 'Carregando' })).toBeInTheDocument();
  });

  it('o evento auth:unauthorized limpa o usuário e redireciona', async () => {
    mockSession(ANA);
    renderWithProviders(routes(), { route: '/' });
    expect(await screen.findByRole('heading', { name: 'Início' })).toBeInTheDocument();

    act(() => {
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    });

    expect(await screen.findByRole('heading', { name: 'Tela de login' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/entrar?voltar=%2F');
  });

  it('usuário logado em /entrar é levado para /', async () => {
    mockSession(ANA);

    renderWithProviders(routes(), { route: '/entrar' });

    expect(await screen.findByRole('heading', { name: 'Início' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/);
  });

  it('usuário logado em /entrar?voltar=… vai para a rota pedida', async () => {
    mockSession(ANA);

    renderWithProviders(routes(), { route: `/entrar?voltar=${encodeURIComponent('/grupos/7')}` });

    expect(await screen.findByRole('heading', { name: 'Grupo' })).toBeInTheDocument();
  });
});
