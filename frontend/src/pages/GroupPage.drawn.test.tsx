import { act, screen, within } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { FakeEventSource } from '../test/eventSource';
import { mockGroup, openGroup } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { GroupPage } from './GroupPage';

const READY = openGroup({
  participantCount: 3,
  canDraw: true,
  participants: [...openGroup().participants, { userId: 'c-3', name: 'Carla', organizer: false }],
});
const DRAWN = {
  ...READY,
  status: 'DRAWN' as const,
  canDraw: false,
  drawnAt: '2026-12-20T15:00:00Z',
};

function renderGroup() {
  mockSession(ANA);
  return renderWithProviders(
    <Routes>
      <Route path="/grupos/:id" element={<GroupPage />} />
    </Routes>,
    { route: '/grupos/g-1' },
  );
}

describe('GroupPage — status DRAWN', () => {
  it('mostra "Sorteado" com a data e o cartão do amigo secreto', async () => {
    mockGroup(DRAWN);
    renderGroup();

    expect(await screen.findByText('Sorteado')).toBeInTheDocument();
    expect(screen.getByText(/Sorteio realizado em 20\/12\/2026/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Seu amigo secreto' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revelar' })).toBeInTheDocument();
  });

  it('não mostra código, sortear, entrar, sair nem remover', async () => {
    mockGroup(DRAWN);
    renderGroup();
    await screen.findByText('Sorteado');

    expect(screen.queryByText('K7PQ-M2XA')).not.toBeInTheDocument();
    for (const name of ['Realizar sorteio', 'Sair do grupo', 'Remover', 'Copiar', 'Compartilhar']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  it('a lista mostra só os nomes, sem indicar quem tirou quem', async () => {
    mockGroup(DRAWN);
    renderGroup();

    const list = await screen.findByRole('list', { name: 'Participantes' });
    const items = within(list).getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      'Ana (você)Organizador',
      'Bruno',
      'Carla',
    ]);
  });

  it('ao receber group-drawn estando OPEN, passa para DRAWN com o aviso', async () => {
    mockGroup(READY, DRAWN);
    renderGroup();
    expect(await screen.findByText('Aguardando sorteio')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Realizar sorteio' })).toBeEnabled();

    act(() => FakeEventSource.latest().emit('group-drawn', { status: 'DRAWN' }));

    expect(
      await screen.findByText(
        'O sorteio foi realizado! Toque em Revelar para ver quem você tirou.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Sorteado')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revelar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Realizar sorteio' })).not.toBeInTheDocument();
  });
});
