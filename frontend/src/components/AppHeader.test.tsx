import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { AppHeader } from './AppHeader';

describe('AppHeader', () => {
  it('sem sessão mostra só o nome do app', async () => {
    mockSession(null);
    renderWithProviders(<AppHeader />);

    expect(screen.getByRole('banner')).toHaveTextContent('Amigo Secreto');
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByRole('button', { name: 'Sair' })).not.toBeInTheDocument();
  });

  it('com sessão mostra o nome do usuário e o botão Sair, que encerra a sessão', async () => {
    mockSession(ANA);
    let loggedOut = false;
    server.use(
      http.post('/api/v1/auth/logout', () => {
        loggedOut = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<AppHeader />, { route: '/grupos/1' });

    expect(await screen.findByText('Ana')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sair' }));

    expect(loggedOut).toBe(true);
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/entrar$/);
    expect(screen.queryByRole('button', { name: 'Sair' })).not.toBeInTheDocument();
  });

  it('mesmo se o logout falhar na rede, o usuário sai da sessão local', async () => {
    mockSession(ANA);
    server.use(http.post('/api/v1/auth/logout', () => HttpResponse.error()));
    renderWithProviders(<AppHeader />);

    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }));

    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/entrar$/);
  });
});
