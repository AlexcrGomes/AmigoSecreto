import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { GroupDetail, MyDependent } from '../../api/groups';
import { useGroup } from '../../hooks/useGroup';
import { mockGroup, openGroup, problem } from '../../test/groups';
import { ANA, mockSession, renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { DependentsCard } from './DependentsCard';

function dependent(id: string, name: string, confirmed = true): MyDependent {
  return {
    id,
    name,
    confirmed,
    wishlist: { items: [], maxItems: 5, editable: true },
    exitRequest: null,
  };
}

const LUCAS = dependent('d-1', 'Lucas');

/** O cartão com a consulta do grupo viva, como na tela do grupo. */
function WithLiveGroup() {
  const group = useGroup('g-1');
  return group.data ? <DependentsCard group={group.data} /> : null;
}

function renderCard(...versions: GroupDetail[]) {
  mockSession(ANA);
  mockGroup(...versions);
  renderWithProviders(<WithLiveGroup />);
}

describe('DependentsCard', () => {
  it('explica para quem é e que só o responsável verá o resultado', async () => {
    renderCard(openGroup());

    expect(await screen.findByRole('heading', { name: 'Meus dependentes' })).toBeInTheDocument();
    expect(
      screen.getByText(
        'Adicione quem não tem e-mail, como uma criança. Só você verá quem ele tirar.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('0 de 5')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Adicionar dependente' })).toBeEnabled();
  });

  it('adiciona um dependente pelo nome e recarrega o grupo', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/groups/g-1/my-dependents', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(LUCAS, { status: 201 });
      }),
    );
    renderCard(openGroup(), openGroup({ myDependents: [LUCAS] }));

    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar dependente' }));
    await userEvent.type(screen.getByLabelText('Nome'), '  Lucas ');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Lucas foi adicionado ao grupo.')).toBeInTheDocument();
    expect(body).toEqual({ name: 'Lucas' });
    const list = await screen.findByRole('list', { name: 'Meus dependentes' });
    expect(within(list).getByText('Lucas')).toBeInTheDocument();
    expect(screen.getByText('1 de 5')).toBeInTheDocument();
  });

  it('nome curto é recusado no próprio formulário', async () => {
    renderCard(openGroup());

    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar dependente' }));
    await userEvent.type(screen.getByLabelText('Nome'), ' L ');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Informe um nome de 2 a 80 caracteres.')).toBeInTheDocument();
  });

  it('erros do servidor aparecem no formulário', async () => {
    server.use(
      http.post('/api/v1/groups/g-1/my-dependents', () =>
        problem(409, 'Você pode adicionar até 5 dependentes.', '/problems/dependents-limit'),
      ),
    );
    renderCard(openGroup());

    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar dependente' }));
    await userEvent.type(screen.getByLabelText('Nome'), 'Lucas');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Você pode adicionar até 5 dependentes.',
    );
  });

  it('mostra a confirmação de cada dependente, que acompanha a do responsável', async () => {
    renderCard(openGroup({ myDependents: [LUCAS, dependent('d-2', 'Bia', false)] }));

    const list = await screen.findByRole('list', { name: 'Meus dependentes' });
    const [lucas, bia] = within(list).getAllByRole('listitem');
    expect(lucas).toHaveTextContent('Confirmado junto com você');
    expect(bia).toHaveTextContent('Pendente — confirme a sua participação');
  });

  it('renomeia com o nome atual preenchido', async () => {
    let body: unknown;
    server.use(
      http.put('/api/v1/groups/g-1/my-dependents/d-1', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...LUCAS, name: 'Lucas M.' });
      }),
    );
    renderCard(openGroup({ myDependents: [LUCAS] }));

    await userEvent.click(await screen.findByRole('button', { name: 'Renomear Lucas' }));
    const input = screen.getByLabelText('Nome');
    expect(input).toHaveValue('Lucas');
    await userEvent.clear(input);
    await userEvent.type(input, 'Lucas M.');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Nome atualizado.')).toBeInTheDocument();
    expect(body).toEqual({ name: 'Lucas M.' });
  });

  it('remove com confirmação', async () => {
    let removed = false;
    server.use(
      http.delete('/api/v1/groups/g-1/my-dependents/d-1', () => {
        removed = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderCard(openGroup({ myDependents: [LUCAS] }), openGroup());

    await userEvent.click(await screen.findByRole('button', { name: 'Remover Lucas' }));
    const dialog = screen.getByRole('dialog', { name: 'Remover dependente?' });
    expect(dialog).toHaveTextContent('Lucas sairá do grupo, junto com a lista de desejos dele.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remover' }));

    expect(await screen.findByText('Lucas não participa mais do grupo.')).toBeInTheDocument();
    expect(removed).toBe(true);
  });

  it.each([
    ['group-full' as const, 'O grupo está cheio (100 participantes).'],
    ['dependents-limit' as const, 'Você pode adicionar até 5 dependentes.'],
    [
      'dependents-locked' as const,
      'Depois da saída de um dependente seu, não dá para adicionar outros neste grupo.',
    ],
  ])('bloqueado por %s explica o motivo e esconde o botão', async (reason, text) => {
    renderCard(openGroup({ canAddDependents: false, dependentsBlockedReason: reason }));

    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Adicionar dependente' })).not.toBeInTheDocument();
  });

  it('depois do sorteio não há como adicionar, renomear nem remover', async () => {
    renderCard(
      openGroup({
        status: 'DRAWN',
        myDependents: [LUCAS],
        canAddDependents: false,
        dependentsBlockedReason: 'group-already-drawn',
      }),
    );

    expect(await screen.findByText('Lucas')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Adicionar dependente' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Renomear Lucas' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remover Lucas' })).not.toBeInTheDocument();
  });

  it('depois do sorteio, sem dependentes, o cartão some', () => {
    mockSession(ANA);
    renderWithProviders(
      <DependentsCard group={openGroup({ status: 'DRAWN', canAddDependents: false })} />,
    );

    expect(screen.queryByRole('heading', { name: 'Meus dependentes' })).not.toBeInTheDocument();
  });

  it('o nome aparece como texto, nunca como HTML', async () => {
    renderCard(openGroup({ myDependents: [dependent('d-3', '<b>Davi</b>')] }));

    expect(await screen.findByText('<b>Davi</b>')).toBeInTheDocument();
  });

  it('depois do sorteio, pede a saída do dependente com confirmação', async () => {
    let asked = false;
    server.use(
      http.post('/api/v1/groups/g-1/my-dependents/d-1/exit-request', () => {
        asked = true;
        return HttpResponse.json(
          { id: 'r-1', userId: 'd-1', name: 'Lucas', requestedAt: '2026-12-02T15:00:00Z' },
          { status: 201 },
        );
      }),
    );
    renderCard(openGroup({ status: 'DRAWN', myDependents: [LUCAS], canAddDependents: false }));

    await userEvent.click(await screen.findByRole('button', { name: 'Pedir saída de Lucas' }));
    const dialog = screen.getByRole('dialog', { name: 'Pedir saída de Lucas?' });
    expect(dialog).toHaveTextContent('não poderá adicionar outros dependentes neste grupo');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Pedir saída' }));

    expect(await screen.findByText('Pedido enviado ao organizador.')).toBeInTheDocument();
    expect(asked).toBe(true);
  });

  it('com o pedido pendente, mostra a situação e cancela', async () => {
    let cancelled = false;
    server.use(
      http.delete('/api/v1/groups/g-1/my-dependents/d-1/exit-request', () => {
        cancelled = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const pending = {
      ...LUCAS,
      exitRequest: { id: 'r-1', userId: 'd-1', name: 'Lucas', requestedAt: '2026-12-02T15:00:00Z' },
    };
    renderCard(openGroup({ status: 'DRAWN', myDependents: [pending], canAddDependents: false }));

    expect(
      await screen.findByText('Saída de Lucas pendente: aguardando o organizador.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pedir saída de Lucas' })).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByRole('button', { name: 'Cancelar o pedido de saída de Lucas' }),
    );

    expect(await screen.findByText('Pedido de saída cancelado.')).toBeInTheDocument();
    expect(cancelled).toBe(true);
  });

  it('erro ao cancelar aparece no cartão', async () => {
    server.use(
      http.delete('/api/v1/groups/g-1/my-dependents/d-1/exit-request', () =>
        problem(
          409,
          'Este dependente não tem pedido de saída pendente.',
          '/problems/exit-request-not-pending',
        ),
      ),
    );
    const pending = {
      ...LUCAS,
      exitRequest: { id: 'r-1', userId: 'd-1', name: 'Lucas', requestedAt: '2026-12-02T15:00:00Z' },
    };
    renderCard(openGroup({ status: 'DRAWN', myDependents: [pending], canAddDependents: false }));

    await userEvent.click(
      await screen.findByRole('button', { name: 'Cancelar o pedido de saída de Lucas' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este dependente não tem pedido de saída pendente.',
    );
  });
});
