import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import type { ExitRequestNotice } from '../../api/exitRequests';
import { BRUNO_ID, openGroup, problem } from '../../test/groups';
import { ANA, mockSession, renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { ExitRequestDecisionNotice, ExitRequestPanel, decisionMessage } from './ExitRequestPanel';

/** Ana é participante (não organizadora) de um grupo sorteado. */
const DRAWN = openGroup({
  status: 'DRAWN',
  drawnAt: '2026-12-01T15:00:00Z',
  organizer: false,
  drawBlockedReason: null,
  participants: [
    { userId: BRUNO_ID, name: 'Bruno', organizer: true, confirmed: true },
    { userId: ANA.id, name: 'Ana', organizer: false, confirmed: true },
  ],
});

const PENDING = openGroup({
  ...DRAWN,
  myExitRequest: {
    id: 'r-1',
    userId: ANA.id,
    name: 'Ana',
    requestedAt: '2026-12-02T12:00:00Z',
  },
});

function notice(overrides: Partial<ExitRequestNotice> = {}): ExitRequestNotice {
  return {
    id: 'r-1',
    groupId: 'g-1',
    groupName: 'Natal 2026',
    status: 'REJECTED',
    decidedAt: '2026-12-03T12:00:00Z',
    ...overrides,
  };
}

describe('ExitRequestPanel', () => {
  it('pedir para sair explica que o organizador decide e que o sorteio será refeito', async () => {
    let asked = false;
    server.use(
      http.post('/api/v1/groups/g-1/exit-requests', () => {
        asked = true;
        return HttpResponse.json(PENDING.myExitRequest, { status: 201 });
      }),
      http.get('/api/v1/groups/g-1', () => HttpResponse.json(PENDING)),
    );
    mockSession(ANA);
    renderWithProviders(<ExitRequestPanel group={DRAWN} />);

    await userEvent.click(screen.getByRole('button', { name: 'Preciso sair do grupo' }));

    const dialog = screen.getByRole('dialog', { name: 'Pedir para sair?' });
    expect(dialog).toHaveTextContent(
      'O organizador vai decidir. Se ele aprovar, você sai do grupo e o sorteio será refeito para todos.',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Pedir para sair' }));

    expect(await screen.findByText('Pedido enviado ao organizador.')).toBeInTheDocument();
    expect(asked).toBe(true);
  });

  it('com pedido pendente, mostra que aguarda o organizador e permite cancelar', async () => {
    let cancelled = false;
    server.use(
      http.delete('/api/v1/groups/g-1/exit-requests/mine', () => {
        cancelled = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.get('/api/v1/groups/g-1', () => HttpResponse.json(DRAWN)),
    );
    mockSession(ANA);
    renderWithProviders(<ExitRequestPanel group={PENDING} />);

    expect(
      screen.getByText('Seu pedido de saída está aguardando o organizador.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Preciso sair do grupo' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar pedido' }));

    expect(await screen.findByText('Pedido de saída cancelado.')).toBeInTheDocument();
    expect(cancelled).toBe(true);
  });

  it('erro ao cancelar aparece no cartão', async () => {
    server.use(
      http.delete('/api/v1/groups/g-1/exit-requests/mine', () =>
        problem(404, 'Você não tem pedido de saída pendente neste grupo.'),
      ),
    );
    mockSession(ANA);
    renderWithProviders(<ExitRequestPanel group={PENDING} />);

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar pedido' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Você não tem pedido de saída pendente neste grupo.',
    );
  });
});

describe('ExitRequestDecisionNotice', () => {
  it('mostra o texto de cada situação', () => {
    expect(decisionMessage(notice(), 'DRAWN')).toBe('O organizador recusou seu pedido de saída.');
    expect(decisionMessage(notice({ status: 'CANCELLED' }), 'OPEN')).toBe(
      'Seu pedido foi cancelado porque o sorteio foi desfeito. Agora você pode sair do grupo diretamente.',
    );
    expect(decisionMessage(notice({ status: 'CANCELLED' }), 'FINISHED')).toBe(
      'Seu pedido foi cancelado porque o evento aconteceu e o grupo foi finalizado.',
    );
  });

  it('"Entendi" marca o aviso como visto', async () => {
    const onAcknowledge = vi.fn().mockResolvedValue(undefined);
    mockSession(ANA);
    renderWithProviders(
      <ExitRequestDecisionNotice notice={notice()} status="DRAWN" onAcknowledge={onAcknowledge} />,
    );

    expect(screen.getByText('O organizador recusou seu pedido de saída.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Entendi' }));

    expect(onAcknowledge).toHaveBeenCalledWith('r-1');
  });

  it('aviso do pedido de um dependente fala do dependente (feature 004)', () => {
    const lucas = notice({ subjectName: 'Lucas' });
    expect(decisionMessage(lucas, 'DRAWN')).toBe(
      'O organizador recusou o pedido de saída de Lucas.',
    );
    expect(decisionMessage({ ...lucas, status: 'CANCELLED' }, 'OPEN')).toBe(
      'O pedido de saída de Lucas foi cancelado porque o sorteio foi desfeito. Agora você pode removê-lo diretamente.',
    );
    expect(decisionMessage({ ...lucas, status: 'CANCELLED' }, 'FINISHED')).toBe(
      'O pedido de saída de Lucas foi cancelado porque o evento aconteceu e o grupo foi finalizado.',
    );
  });
});
