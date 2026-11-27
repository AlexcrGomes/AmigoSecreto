import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { GroupDetail, MyWishlist, WishItem } from '../../api/groups';
import { useGroup } from '../../hooks/useGroup';
import { mockGroup, openGroup, problem } from '../../test/groups';
import { ANA, mockSession, renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { WishlistEditor } from './WishlistEditor';

const LIVRO: WishItem = { id: 'i-1', description: 'Livro de receitas', url: null };
const CANECA: WishItem = { id: 'i-2', description: 'Caneca', url: 'https://loja.com/caneca' };

function listOf(items: WishItem[], editable = true): MyWishlist {
  return { items, maxItems: 5, editable };
}

/** O editor com a consulta do grupo viva, como na tela do grupo. */
function WithLiveGroup() {
  const group = useGroup('g-1');
  return group.data ? <WishlistEditor group={group.data} /> : null;
}

function renderEditor(group: GroupDetail) {
  mockSession(ANA);
  mockGroup(group);
  renderWithProviders(<WithLiveGroup />);
}

describe('WishlistEditor', () => {
  it('lista vazia convida a sugerir e explica quem vê', async () => {
    renderEditor(openGroup());

    expect(
      await screen.findByText('Você ainda não sugeriu presentes. Ajude quem tirar você.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Só você e quem tirar você veem esta lista.')).toBeInTheDocument();
    expect(screen.getByText('0 de 5')).toBeInTheDocument();
  });

  it('mostra os itens na ordem, com o link como texto', async () => {
    renderEditor(openGroup({ myWishlist: listOf([LIVRO, CANECA]) }));

    const list = await screen.findByRole('list', { name: 'Minha lista de desejos' });
    const items = within(list).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Livro de receitas');
    expect(items[1]).toHaveTextContent('Caneca');
    expect(items[1]).toHaveTextContent('https://loja.com/caneca');
    expect(screen.getByText('2 de 5')).toBeInTheDocument();
  });

  it('adiciona uma sugestão com link e atualiza a lista', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/groups/g-1/wishlist/mine/items', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(listOf([CANECA]), { status: 201 });
      }),
    );
    renderEditor(openGroup());

    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar sugestão' }));
    await userEvent.type(screen.getByLabelText('Sugestão'), '  Caneca  ');
    await userEvent.type(screen.getByLabelText('Link (opcional)'), 'https://loja.com/caneca');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Sugestão adicionada.')).toBeInTheDocument();
    expect(body).toEqual({ description: 'Caneca', url: 'https://loja.com/caneca' });
    expect(await screen.findByText('1 de 5')).toBeInTheDocument();
  });

  it('sem link, envia null', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/groups/g-1/wishlist/mine/items', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(listOf([LIVRO]), { status: 201 });
      }),
    );
    renderEditor(openGroup());

    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar sugestão' }));
    await userEvent.type(screen.getByLabelText('Sugestão'), 'Livro de receitas');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await screen.findByText('Sugestão adicionada.');
    expect(body).toEqual({ description: 'Livro de receitas', url: null });
  });

  it('valida a descrição e o link antes de enviar', async () => {
    renderEditor(openGroup());

    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar sugestão' }));
    await userEvent.type(screen.getByLabelText('Link (opcional)'), 'javascript:alert(1)');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(
      await screen.findByText('Descreva a sugestão (até 120 caracteres).'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Informe um link que comece com http:// ou https://.'),
    ).toBeInTheDocument();
  });

  it('erro do servidor aparece no formulário', async () => {
    server.use(
      http.post('/api/v1/groups/g-1/wishlist/mine/items', () =>
        problem(409, 'A lista tem no máximo 5 sugestões.', '/problems/wishlist-full'),
      ),
    );
    renderEditor(openGroup());

    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar sugestão' }));
    await userEvent.type(screen.getByLabelText('Sugestão'), 'Livro');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A lista tem no máximo 5 sugestões.',
    );
  });

  it('edita uma sugestão', async () => {
    let body: unknown;
    server.use(
      http.put('/api/v1/groups/g-1/wishlist/mine/items/i-1', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(listOf([{ ...LIVRO, description: 'Livro de bolos' }]));
      }),
    );
    renderEditor(openGroup({ myWishlist: listOf([LIVRO]) }));

    await userEvent.click(await screen.findByRole('button', { name: 'Editar Livro de receitas' }));
    const description = screen.getByLabelText('Sugestão');
    expect(description).toHaveValue('Livro de receitas');
    await userEvent.clear(description);
    await userEvent.type(description, 'Livro de bolos');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Sugestão atualizada.')).toBeInTheDocument();
    expect(body).toEqual({ description: 'Livro de bolos', url: null });
    expect(await screen.findByText('Livro de bolos')).toBeInTheDocument();
  });

  it('remove uma sugestão depois de confirmar', async () => {
    let removed = false;
    server.use(
      http.delete('/api/v1/groups/g-1/wishlist/mine/items/i-1', () => {
        removed = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderEditor(openGroup({ myWishlist: listOf([LIVRO, CANECA]) }));

    await userEvent.click(await screen.findByRole('button', { name: 'Remover Livro de receitas' }));
    const dialog = screen.getByRole('dialog', { name: 'Remover sugestão?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remover' }));

    expect(await screen.findByText('Sugestão removida.')).toBeInTheDocument();
    expect(removed).toBe(true);
    expect(screen.queryByText('Livro de receitas')).not.toBeInTheDocument();
    expect(screen.getByText('1 de 5')).toBeInTheDocument();
  });

  it('com 5 sugestões, não deixa adicionar outra', async () => {
    const five = [1, 2, 3, 4, 5].map((n) => ({
      id: `i-${n}`,
      description: `Item ${n}`,
      url: null,
    }));
    renderEditor(openGroup({ myWishlist: listOf(five) }));

    expect(await screen.findByText('A lista tem no máximo 5 sugestões.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Adicionar sugestão' })).not.toBeInTheDocument();
  });

  it('em grupo finalizado, fica somente leitura', async () => {
    renderEditor(openGroup({ status: 'FINISHED', myWishlist: listOf([LIVRO], false) }));

    expect(await screen.findByText('Livro de receitas')).toBeInTheDocument();
    for (const name of [
      'Adicionar sugestão',
      'Editar Livro de receitas',
      'Remover Livro de receitas',
    ]) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  it('texto com HTML aparece como texto', async () => {
    renderEditor(
      openGroup({ myWishlist: listOf([{ id: 'i-9', description: '<b>caro</b>', url: null }]) }),
    );

    expect(await screen.findByText('<b>caro</b>')).toBeInTheDocument();
  });
});

describe('WishlistEditor — lista de um dependente (US3 da 004)', () => {
  const LUCAS = {
    id: 'd-1',
    name: 'Lucas',
    confirmed: true,
    wishlist: { items: [], maxItems: 5, editable: true },
    exitRequest: null,
  };

  /** O editor do dependente com a consulta do grupo viva. */
  function WithLiveDependent() {
    const group = useGroup('g-1');
    const dependent = group.data?.myDependents[0];
    return group.data && dependent ? (
      <WishlistEditor group={group.data} dependent={dependent} />
    ) : null;
  }

  it('usa as rotas do dependente e atualiza só a lista dele', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/groups/g-1/my-dependents/d-1/wishlist/items', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(listOf([LIVRO]), { status: 201 });
      }),
    );
    mockSession(ANA);
    mockGroup(openGroup({ myDependents: [LUCAS] }));
    renderWithProviders(<WithLiveDependent />);

    expect(
      await screen.findByRole('heading', { name: 'Lista de desejos de Lucas' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Só você e quem tirar Lucas veem esta lista.')).toBeInTheDocument();
    expect(
      screen.getByText('Lucas ainda não tem sugestões. Ajude quem tirar Lucas.'),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Adicionar sugestão' }));
    await userEvent.type(screen.getByLabelText('Sugestão'), 'Livro de receitas');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Sugestão adicionada.')).toBeInTheDocument();
    expect(body).toEqual({ description: 'Livro de receitas', url: null });
    expect(await screen.findByText('1 de 5')).toBeInTheDocument();
  });

  it('remove um item da lista do dependente', async () => {
    let removed = false;
    server.use(
      http.delete('/api/v1/groups/g-1/my-dependents/d-1/wishlist/items/i-1', () => {
        removed = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    mockSession(ANA);
    mockGroup(openGroup({ myDependents: [{ ...LUCAS, wishlist: listOf([LIVRO]) }] }));
    renderWithProviders(<WithLiveDependent />);

    await userEvent.click(await screen.findByRole('button', { name: 'Remover Livro de receitas' }));
    const dialog = screen.getByRole('dialog', { name: 'Remover sugestão?' });
    expect(dialog).toHaveTextContent('sairá da lista de Lucas');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remover' }));

    expect(await screen.findByText('Sugestão removida.')).toBeInTheDocument();
    expect(removed).toBe(true);
  });
});
