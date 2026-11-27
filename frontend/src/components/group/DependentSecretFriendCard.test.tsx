import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { DependentSecretFriend, MyDependent } from '../../api/groups';
import { problem } from '../../test/groups';
import { server } from '../../test/server';
import { ToastProvider } from '../ui/Toast';
import {
  DependentSecretFriendCards,
  dependentsSecretFriendsKey,
} from './DependentSecretFriendCard';

function dependent(id: string, name: string): MyDependent {
  return {
    id,
    name,
    confirmed: true,
    wishlist: { items: [], maxItems: 5, editable: true },
    exitRequest: null,
  };
}

const LUCAS = dependent('d-1', 'Lucas');
const BIA = dependent('d-2', 'Bia');
const RESULTS: DependentSecretFriend[] = [
  {
    dependentId: 'd-1',
    dependentName: 'Lucas',
    secretFriend: {
      name: 'Carla',
      wishlist: [{ description: 'Livro', url: null }],
      wishlistUpdatedAt: null,
    },
  },
  {
    dependentId: 'd-2',
    dependentName: 'Bia',
    secretFriend: { name: 'Davi', wishlist: [], wishlistUpdatedAt: null },
  },
];

function mockResults(results: DependentSecretFriend[] = RESULTS) {
  let calls = 0;
  server.use(
    http.get('/api/v1/groups/g-1/my-dependents/secret-friends', () => {
      calls += 1;
      return HttpResponse.json(results);
    }),
  );
  return () => calls;
}

function renderCards(dependents: MyDependent[] = [LUCAS, BIA], giftValue: number | null = null) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <DependentSecretFriendCards groupId="g-1" dependents={dependents} giftValue={giftValue} />
      </ToastProvider>
    </QueryClientProvider>,
  );
  return queryClient;
}

describe('DependentSecretFriendCards', () => {
  afterEach(() => vi.restoreAllMocks());

  it('um cartão por dependente, todos ocultos e sem buscar nada', () => {
    const calls = mockResults();
    renderCards();

    expect(screen.getByRole('heading', { name: 'Amigo secreto de Lucas' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Amigo secreto de Bia' })).toBeInTheDocument();
    expect(screen.getAllByLabelText('Nome oculto')).toHaveLength(2);
    expect(screen.getByText('Só você vê quem Lucas tirou.')).toBeInTheDocument();
    expect(calls()).toBe(0);
  });

  it('revelar um cartão mostra só aquele resultado, com o valor e a lista', async () => {
    const calls = mockResults();
    renderCards([LUCAS, BIA], 80);

    await userEvent.click(screen.getByRole('button', { name: 'Revelar o amigo secreto de Lucas' }));

    expect(await screen.findByText('Carla')).toBeInTheDocument();
    expect(screen.getByText('Livro')).toBeInTheDocument();
    expect(screen.getByText(/R\$\s?80/)).toBeInTheDocument();
    expect(screen.queryByText('Davi')).not.toBeInTheDocument();
    expect(screen.getAllByLabelText('Nome oculto')).toHaveLength(1);

    await userEvent.click(screen.getByRole('button', { name: 'Revelar o amigo secreto de Bia' }));
    expect(await screen.findByText('Davi')).toBeInTheDocument();
    expect(calls()).toBe(1);
  });

  it('ocultar esconde o cartão; ao ocultar o último, os resultados saem da memória', async () => {
    mockResults();
    const queryClient = renderCards();

    await userEvent.click(screen.getByRole('button', { name: 'Revelar o amigo secreto de Lucas' }));
    await userEvent.click(screen.getByRole('button', { name: 'Revelar o amigo secreto de Bia' }));
    expect(await screen.findByText('Davi')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar o amigo secreto de Lucas' }));
    expect(screen.queryByText('Carla')).not.toBeInTheDocument();
    expect(screen.getByText('Davi')).toBeInTheDocument();
    expect(queryClient.getQueryData(dependentsSecretFriendsKey('g-1'))).toBeDefined();

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar o amigo secreto de Bia' }));
    expect(screen.queryByText('Davi')).not.toBeInTheDocument();
    expect(queryClient.getQueryData(dependentsSecretFriendsKey('g-1'))).toBeUndefined();
  });

  it('nada vai para o armazenamento do navegador', async () => {
    mockResults();
    const local = vi.spyOn(Storage.prototype, 'setItem');
    renderCards();

    await userEvent.click(screen.getByRole('button', { name: 'Revelar o amigo secreto de Lucas' }));
    expect(await screen.findByText('Carla')).toBeInTheDocument();

    expect(local).not.toHaveBeenCalled();
  });

  it('um erro aparece no cartão e "Revelar" tenta de novo', async () => {
    server.use(
      http.get('/api/v1/groups/g-1/my-dependents/secret-friends', () =>
        problem(409, 'O sorteio deste grupo ainda não foi realizado.', '/problems/group-not-drawn'),
      ),
    );
    renderCards([LUCAS]);

    await userEvent.click(screen.getByRole('button', { name: 'Revelar o amigo secreto de Lucas' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O sorteio deste grupo ainda não foi realizado.',
    );

    mockResults();
    await userEvent.click(screen.getByRole('button', { name: 'Revelar o amigo secreto de Lucas' }));
    expect(await screen.findByText('Carla')).toBeInTheDocument();
  });

  it('sem dependentes, nenhum cartão e nenhuma chamada', () => {
    const calls = mockResults();
    renderCards([]);

    expect(screen.queryByRole('heading', { name: /Amigo secreto de/ })).not.toBeInTheDocument();
    expect(calls()).toBe(0);
  });
});
