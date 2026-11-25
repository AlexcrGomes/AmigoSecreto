import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../ui/Toast';
import { problem } from '../../test/groups';
import { server } from '../../test/server';
import { SecretFriendCard, secretFriendKey } from './SecretFriendCard';

function renderCard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <SecretFriendCard groupId="g-1" />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return queryClient;
}

describe('SecretFriendCard', () => {
  afterEach(() => vi.restoreAllMocks());

  it('começa com o nome oculto e sem buscar nada', () => {
    let calls = 0;
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () => {
        calls += 1;
        return HttpResponse.json({ name: 'Carla' });
      }),
    );
    renderCard();

    expect(screen.getByText('••••••')).toBeInTheDocument();
    expect(screen.queryByText('Carla')).not.toBeInTheDocument();
    expect(calls).toBe(0);
  });

  it('"Revelar" busca e mostra o nome; "Ocultar" esconde e descarta da memória', async () => {
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () => HttpResponse.json({ name: 'Carla' })),
    );
    const queryClient = renderCard();

    await userEvent.click(screen.getByRole('button', { name: 'Revelar' }));
    expect(await screen.findByText('Carla')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar' }));
    expect(screen.queryByText('Carla')).not.toBeInTheDocument();
    expect(screen.getByText('••••••')).toBeInTheDocument();
    expect(queryClient.getQueryData(secretFriendKey('g-1'))).toBeUndefined();
  });

  it('nada é gravado em localStorage nem sessionStorage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () => HttpResponse.json({ name: 'Carla' })),
    );
    renderCard();

    await userEvent.click(screen.getByRole('button', { name: 'Revelar' }));
    await screen.findByText('Carla');

    expect(setItem).not.toHaveBeenCalled();
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('Carla');
  });

  it('a consulta não fica em cache (gcTime 0)', async () => {
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () => HttpResponse.json({ name: 'Carla' })),
    );
    const queryClient = renderCard();

    await userEvent.click(screen.getByRole('button', { name: 'Revelar' }));
    await screen.findByText('Carla');

    const query = queryClient.getQueryCache().find({ queryKey: secretFriendKey('g-1') });
    expect(query?.options).toMatchObject({ gcTime: 0, staleTime: 0 });
  });

  it('erro ao revelar mostra a mensagem e mantém o nome oculto', async () => {
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () =>
        problem(409, 'O sorteio deste grupo ainda não foi realizado.', '/problems/group-not-drawn'),
      ),
    );
    renderCard();

    await userEvent.click(screen.getByRole('button', { name: 'Revelar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O sorteio deste grupo ainda não foi realizado.',
    );
    expect(screen.getByText('••••••')).toBeInTheDocument();
  });

  // ---- lista de desejos de quem o usuário tirou (US2 da 003) ----

  it('"Revelar" mostra também a lista de desejos; "Ocultar" esconde os dois', async () => {
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () =>
        HttpResponse.json({
          name: 'Carla',
          wishlist: [{ description: 'Perfume', url: null }],
          wishlistUpdatedAt: '2026-12-21T15:00:00Z',
        }),
      ),
    );
    renderCard();

    await userEvent.click(screen.getByRole('button', { name: 'Revelar' }));
    expect(await screen.findByText('Perfume')).toBeInTheDocument();
    expect(screen.getByText('Atualizada em 21/12')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar' }));
    expect(screen.queryByText('Carla')).not.toBeInTheDocument();
    expect(screen.queryByText('Perfume')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Lista de desejos' })).not.toBeInTheDocument();
  });

  it('antes de revelar, a lista não aparece', () => {
    renderCard();

    expect(screen.queryByRole('heading', { name: 'Lista de desejos' })).not.toBeInTheDocument();
  });

  it('mostra o valor do presente junto da lista revelada', async () => {
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () =>
        HttpResponse.json({ name: 'Carla', wishlist: [], wishlistUpdatedAt: null }),
      ),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <SecretFriendCard groupId="g-1" giftValue={80} />
        </ToastProvider>
      </QueryClientProvider>,
    );

    expect(screen.queryByText(/Valor do presente/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Revelar' }));

    expect(await screen.findByText('R$ 80')).toBeInTheDocument();
  });
});
