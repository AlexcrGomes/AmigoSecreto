import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { openGroup, problem } from '../../test/groups';
import { ANA, mockSession, renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { DrawButton } from './DrawButton';

const THREE = openGroup({
  participantCount: 3,
  canDraw: true,
  participants: [...openGroup().participants, { userId: 'c-3', name: 'Carla', organizer: false }],
});

describe('DrawButton', () => {
  it('não-organizador não vê o botão', () => {
    mockSession(ANA);
    renderWithProviders(<DrawButton group={openGroup({ organizer: false })} />);

    expect(screen.queryByRole('button', { name: 'Realizar sorteio' })).not.toBeInTheDocument();
  });

  it('grupo já sorteado não mostra o botão', () => {
    mockSession(ANA);
    renderWithProviders(<DrawButton group={{ ...THREE, status: 'DRAWN', canDraw: false }} />);

    expect(screen.queryByRole('button', { name: 'Realizar sorteio' })).not.toBeInTheDocument();
  });

  it('com menos de 3 participantes fica desabilitado com a dica', () => {
    mockSession(ANA);
    renderWithProviders(<DrawButton group={openGroup()} />);

    const button = screen.getByRole('button', { name: 'Realizar sorteio' });
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription('São necessários pelo menos 3 participantes');
  });

  it('o modal avisa que ninguém mais poderá entrar ou sair, e cancelar não sorteia', async () => {
    mockSession(ANA);
    renderWithProviders(<DrawButton group={THREE} />);

    await userEvent.click(screen.getByRole('button', { name: 'Realizar sorteio' }));

    const dialog = screen.getByRole('dialog', { name: 'Realizar o sorteio?' });
    expect(dialog).toHaveTextContent('ninguém mais poderá entrar ou sair');
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('confirmar chama POST /draw com loading e fecha o modal', async () => {
    mockSession(ANA);
    let calls = 0;
    server.use(
      http.post('/api/v1/groups/g-1/draw', async () => {
        calls += 1;
        await delay(50);
        return HttpResponse.json({
          groupId: 'g-1',
          status: 'DRAWN',
          drawnAt: '2026-12-20T15:00:00Z',
        });
      }),
      http.get('/api/v1/groups/g-1', () => HttpResponse.json({ ...THREE, status: 'DRAWN' })),
    );
    renderWithProviders(<DrawButton group={THREE} />);

    await userEvent.click(screen.getByRole('button', { name: 'Realizar sorteio' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sortear' }));

    expect(screen.getByRole('button', { name: /Sortear/ })).toHaveAttribute('aria-busy', 'true');
    expect(await screen.findByText('Sorteio realizado!')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(calls).toBe(1);
  });

  it.each([
    [409, '/problems/group-already-drawn', 'O sorteio deste grupo já foi realizado.'],
    [422, '/problems/not-enough-participants', 'São necessários pelo menos 3 participantes.'],
    [429, '/problems/rate-limited', 'Muitas tentativas. Tente novamente em alguns minutos.'],
  ])('erro %s mostra o detail', async (status, type, detail) => {
    mockSession(ANA);
    server.use(http.post('/api/v1/groups/g-1/draw', () => problem(status, detail, type)));
    renderWithProviders(<DrawButton group={THREE} />);

    await userEvent.click(screen.getByRole('button', { name: 'Realizar sorteio' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sortear' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(detail);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
