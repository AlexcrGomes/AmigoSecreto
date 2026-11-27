import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { setMediaMatcher } from '../test/media';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { AppNavigation } from './AppNavigation';

const WIDE = (query: string) => query.includes('min-width: 768px');

function renderNav(route = '/') {
  mockSession(ANA);
  return renderWithProviders(
    <>
      <AppNavigation />
      <label>
        Campo
        <input type="text" />
      </label>
    </>,
    { route },
  );
}

function mockLogout() {
  const calls = { count: 0 };
  server.use(
    http.post('/api/v1/auth/logout', () => {
      calls.count += 1;
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return calls;
}

describe('AppNavigation — celular (barra de abas embaixo)', () => {
  it('mostra os três destinos e "Sair", com a aba da rota marcada', async () => {
    renderNav('/grupos/g-1');

    const nav = await screen.findByRole('navigation', { name: 'Menu principal' });
    expect(within(nav).getByRole('link', { name: 'Meus grupos' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByRole('link', { name: 'Criar grupo' })).not.toHaveAttribute(
      'aria-current',
    );
    expect(within(nav).getByRole('link', { name: 'Entrar com código' })).toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: 'Sair' })).toBeInTheDocument();
    expect(nav.closest('[data-tabbar]')).not.toBeNull();
  });

  it.each([
    ['/', 'Meus grupos'],
    ['/grupos/novo', 'Criar grupo'],
    ['/entrar-no-grupo', 'Entrar com código'],
  ])('em %s marca "%s"', async (route, label) => {
    renderNav(route);

    const nav = await screen.findByRole('navigation', { name: 'Menu principal' });
    expect(within(nav).getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page');
  });

  it('tocar num destino navega', async () => {
    renderNav('/');

    await userEvent.click(await screen.findByRole('link', { name: 'Criar grupo' }));

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/grupos\/novo$/);
  });

  it('some enquanto um campo de texto está em foco (teclado aberto)', async () => {
    renderNav('/');
    await screen.findByRole('navigation', { name: 'Menu principal' });

    await userEvent.click(screen.getByLabelText('Campo'));
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();

    act(() => screen.getByLabelText('Campo').blur());
    expect(screen.getByRole('navigation', { name: 'Menu principal' })).toBeInTheDocument();
  });
});

describe('AppNavigation — tablet e computador (menu lateral)', () => {
  it('menu lateral com os destinos, o atual marcado, o nome do usuário e "Sair"', async () => {
    setMediaMatcher(WIDE);
    renderNav('/entrar-no-grupo');

    const nav = await screen.findByRole('navigation', { name: 'Menu principal' });
    expect(nav.closest('[data-tabbar]')).toBeNull();
    expect(within(nav).getByRole('link', { name: 'Entrar com código' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByRole('link', { name: 'Meus grupos' })).not.toHaveAttribute(
      'aria-current',
    );
    expect(await within(nav).findByText('Ana')).toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: 'Sair' })).toBeInTheDocument();
    expect(screen.getAllByRole('navigation')).toHaveLength(1);
  });

  it('não some com um campo em foco', async () => {
    setMediaMatcher(WIDE);
    renderNav('/');
    await screen.findByRole('navigation', { name: 'Menu principal' });

    await userEvent.click(screen.getByLabelText('Campo'));

    expect(screen.getByRole('navigation', { name: 'Menu principal' })).toBeInTheDocument();
  });

  it('trocar a largura com a tela aberta troca a forma sem sair da rota', async () => {
    renderNav('/grupos/novo');
    const bar = await screen.findByRole('navigation', { name: 'Menu principal' });
    expect(bar.closest('[data-tabbar]')).not.toBeNull();

    act(() => setMediaMatcher(WIDE));

    const side = screen.getByRole('navigation', { name: 'Menu principal' });
    expect(side.closest('[data-tabbar]')).toBeNull();
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/grupos\/novo$/);
    expect(screen.getAllByRole('navigation')).toHaveLength(1);
  });
});

describe.each([
  ['celular', () => false],
  ['menu lateral', WIDE],
])('"Sair" pede confirmação (%s)', (_name, matcher) => {
  it('cancelar não sai, não chama o servidor e mantém a rota', async () => {
    setMediaMatcher(matcher);
    const logout = mockLogout();
    renderNav('/grupos/novo');

    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }));
    const dialog = screen.getByRole('dialog', { name: 'Sair da sua conta?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(logout.count).toBe(0);
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/grupos\/novo$/);
    expect(screen.getByRole('link', { name: 'Criar grupo' })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('confirmar encerra a sessão e vai para /entrar', async () => {
    setMediaMatcher(matcher);
    const logout = mockLogout();
    renderNav('/');

    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }));
    const dialog = screen.getByRole('dialog', { name: 'Sair da sua conta?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sair' }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/entrar$/));
    expect(logout.count).toBe(1);
  });
});

it('mesmo se o logout falhar na rede, o usuário sai da sessão local', async () => {
  server.use(http.post('/api/v1/auth/logout', () => HttpResponse.error()));
  renderNav('/');

  await userEvent.click(await screen.findByRole('button', { name: 'Sair' }));
  const dialog = screen.getByRole('dialog', { name: 'Sair da sua conta?' });
  await userEvent.click(within(dialog).getByRole('button', { name: 'Sair' }));

  await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/entrar$/));
});
