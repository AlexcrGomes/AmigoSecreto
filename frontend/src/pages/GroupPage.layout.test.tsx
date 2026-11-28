import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { mockGroup, openGroup } from '../test/groups';
import { setMediaMatcher } from '../test/media';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { GroupPage } from './GroupPage';

/** Tela do grupo em dois desenhos (feature 005, US4, research R57). */

const WIDE = (query: string) => query.includes('min-width: 768px');

const DRAWN = openGroup({
  status: 'DRAWN',
  drawnAt: '2026-12-20T15:00:00Z',
  organizer: false,
  participants: [
    { userId: 'b-2', name: 'Bruno', organizer: true, confirmed: true },
    { userId: ANA.id, name: 'Ana', organizer: false, confirmed: true },
    { userId: 'c-3', name: 'Carla', organizer: false, confirmed: true },
  ],
  participantCount: 3,
});

function renderGroup(route = '/grupos/g-1') {
  mockSession(ANA);
  return renderWithProviders(
    <Routes>
      <Route path="/grupos/:id" element={<GroupPage />} />
    </Routes>,
    { route },
  );
}

/** A parte pelo título; uma seção com `hidden` não tem nome acessível, então vai pelo DOM. */
function section(name: string) {
  const heading = screen.getByRole('heading', { name, level: 2, hidden: true });
  const part = heading.closest('section');
  if (!part) throw new Error(`Parte "${name}" não encontrada`);
  return part;
}

describe('GroupPage no celular — abas Resumo · Pessoas · Desejos', () => {
  it('abre no Resumo, com as três partes montadas e só a aberta visível', async () => {
    mockGroup(openGroup());
    renderGroup();

    const tabs = await screen.findByRole('radiogroup', { name: 'Partes do grupo' });
    expect(within(tabs).getByRole('radio', { name: 'Resumo' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(tabs).getByRole('radio', { name: 'Pessoas' })).toBeInTheDocument();
    expect(within(tabs).getByRole('radio', { name: 'Desejos' })).toBeInTheDocument();

    expect(section('Resumo')).not.toHaveAttribute('hidden');
    expect(section('Pessoas')).toHaveAttribute('hidden');
    expect(section('Desejos')).toHaveAttribute('hidden');
    // O resumo tem o código; participantes e listas ficam nas outras abas
    expect(screen.getByText('K7PQ-M2XA')).toBeVisible();
    expect(screen.queryByRole('list', { name: 'Participantes' })).not.toBeInTheDocument();
  });

  it('tocar em "Pessoas" mostra os participantes e põe a aba no endereço', async () => {
    mockGroup(openGroup());
    renderGroup();

    await userEvent.click(await screen.findByRole('radio', { name: 'Pessoas' }));

    expect(screen.getByRole('list', { name: 'Participantes' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Meus dependentes' })).toBeInTheDocument();
    expect(section('Resumo')).toHaveAttribute('hidden');
    expect(screen.getByTestId('location')).toHaveTextContent('/grupos/g-1?aba=pessoas');
  });

  it('"Desejos" tem a minha lista', async () => {
    mockGroup(openGroup());
    renderGroup();

    await userEvent.click(await screen.findByRole('radio', { name: 'Desejos' }));

    expect(screen.getByRole('heading', { name: 'Minha lista de desejos' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/grupos/g-1?aba=desejos');
  });

  it('o endereço com ?aba=desejos abre a aba; valor desconhecido abre o Resumo', async () => {
    mockGroup(openGroup());
    const first = renderGroup('/grupos/g-1?aba=desejos');

    expect(await screen.findByRole('radio', { name: 'Desejos' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(section('Desejos')).not.toHaveAttribute('hidden');
    first.unmount();

    renderGroup('/grupos/g-1?aba=xyz');
    expect(await screen.findByRole('radio', { name: 'Resumo' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
  });

  it('com o amigo secreto revelado, trocar de aba e voltar mantém o nome sem buscar de novo', async () => {
    mockGroup(DRAWN);
    let calls = 0;
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () => {
        calls += 1;
        return HttpResponse.json({ name: 'Carla', wishlist: [], wishlistUpdatedAt: null });
      }),
    );
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Revelar' }));
    expect(await screen.findByText('Carla', { selector: 'p.text-3xl' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: 'Pessoas' }));
    await userEvent.click(screen.getByRole('radio', { name: 'Resumo' }));

    expect(screen.getByText('Carla', { selector: 'p.text-3xl' })).toBeVisible();
    expect(calls).toBe(1);
  });
});

describe('GroupPage no tablet e no computador — duas colunas', () => {
  it('sem abas, todas as partes visíveis; pessoas e desejos na coluna lateral', async () => {
    setMediaMatcher(WIDE);
    mockGroup(openGroup());
    renderGroup('/grupos/g-1?aba=pessoas');

    await screen.findByRole('heading', { name: 'Natal 2026' });
    expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();
    for (const name of ['Resumo', 'Pessoas', 'Desejos']) {
      expect(section(name)).not.toHaveAttribute('hidden');
    }
    const side = screen.getByTestId('group-side');
    expect(within(side).getByRole('region', { name: 'Pessoas' })).toBeInTheDocument();
    expect(within(side).getByRole('region', { name: 'Desejos' })).toBeInTheDocument();
    expect(within(side).queryByRole('region', { name: 'Resumo' })).not.toBeInTheDocument();
  });

  it('girar o tablet com uma sugestão sendo escrita não perde o texto', async () => {
    mockGroup(openGroup());
    renderGroup('/grupos/g-1?aba=desejos');

    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar sugestão' }));
    await userEvent.type(screen.getByLabelText('Sugestão'), 'Livro de receitas');

    act(() => setMediaMatcher(WIDE));

    const dialog = screen.getByRole('dialog', { name: 'Nova sugestão' });
    expect(within(dialog).getByLabelText('Sugestão')).toHaveValue('Livro de receitas');
    expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument();
  });
});
