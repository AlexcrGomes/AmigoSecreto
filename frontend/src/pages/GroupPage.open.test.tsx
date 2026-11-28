import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FakeEventSource } from '../test/eventSource';
import { BRUNO_ID, mockGroup, openGroup, problem } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { setMediaMatcher } from '../test/media';
import { server } from '../test/server';
import { GroupPage } from './GroupPage';

// Desenho largo (≥ 768px): as três partes do grupo ficam visíveis juntas (feature 005, R59)
beforeEach(() => setMediaMatcher((query) => query.includes('min-width: 768px')));

function renderGroup(id = 'g-1') {
  mockSession(ANA);
  return renderWithProviders(
    <Routes>
      <Route path="/grupos/:id" element={<GroupPage />} />
      <Route path="/" element={<h1>Meus grupos</h1>} />
    </Routes>,
    { route: `/grupos/${id}` },
  );
}

describe('GroupPage — status OPEN', () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, 'share');
  });

  it('mostra nome, selo, código em destaque e participantes com o organizador', async () => {
    mockGroup(openGroup());
    renderGroup();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Natal 2026' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Aguardando sorteio')).toBeInTheDocument();
    expect(screen.getByText('K7PQ-M2XA')).toBeInTheDocument();

    const list = screen.getByRole('list', { name: 'Participantes' });
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Ana');
    expect(items[0]).toHaveTextContent('(você)');
    expect(within(items[0] as HTMLElement).getByText('Organizador')).toBeInTheDocument();
    expect(items[1]).toHaveTextContent('Bruno');
    expect(screen.getByText('2 participantes')).toBeInTheDocument();
  });

  it('"Copiar" põe o código na área de transferência e mostra um toast', async () => {
    const user = userEvent.setup();
    mockGroup(openGroup());
    renderGroup();

    await user.click(await screen.findByRole('button', { name: 'Copiar' }));

    expect(await navigator.clipboard.readText()).toBe('K7PQ-M2XA');
    expect(await screen.findByText('Código copiado!')).toBeInTheDocument();
  });

  it('"Compartilhar" usa o navigator.share com o link de entrada', async () => {
    const share = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'share', { value: share, configurable: true });
    mockGroup(openGroup());
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Compartilhar' }));

    expect(share).toHaveBeenCalledWith(
      expect.objectContaining({
        url: `${window.location.origin}/entrar-no-grupo?codigo=K7PQ-M2XA`,
      }),
    );
  });

  it('sem navigator.share, "Compartilhar" copia o link', async () => {
    const user = userEvent.setup();
    mockGroup(openGroup());
    renderGroup();

    await user.click(await screen.findByRole('button', { name: 'Compartilhar' }));

    expect(await navigator.clipboard.readText()).toBe(
      `${window.location.origin}/entrar-no-grupo?codigo=K7PQ-M2XA`,
    );
    expect(await screen.findByText('Link copiado!')).toBeInTheDocument();
  });

  it('cancelar o compartilhamento não mostra erro', async () => {
    const share = vi.fn().mockRejectedValue(new DOMException('cancelado', 'AbortError'));
    Object.defineProperty(navigator, 'share', { value: share, configurable: true });
    mockGroup(openGroup());
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Compartilhar' }));

    expect(share).toHaveBeenCalled();
    expect(screen.queryByText('Link copiado!')).not.toBeInTheDocument();
  });

  it('a lista atualiza ao vivo com participants-changed', async () => {
    mockGroup(
      openGroup(),
      openGroup({
        participants: [
          ...openGroup().participants,
          { userId: 'c-3', name: 'Carla', organizer: false, confirmed: true },
        ],
        participantCount: 3,
      }),
    );
    renderGroup();
    await screen.findByText('2 participantes');

    act(() => FakeEventSource.latest().emit('participants-changed', { participantCount: 3 }));

    expect(await screen.findByText('3 participantes')).toBeInTheDocument();
    expect(screen.getByText('Carla')).toBeInTheDocument();
  });

  it('participante comum não é marcado como organizador', async () => {
    mockGroup(
      openGroup({
        organizer: false,
        participants: [
          { userId: BRUNO_ID, name: 'Bruno', organizer: true, confirmed: true },
          { userId: ANA.id, name: 'Ana', organizer: false, confirmed: true },
        ],
      }),
    );
    renderGroup();

    const list = await screen.findByRole('list', { name: 'Participantes' });
    const [first, second] = within(list).getAllByRole('listitem');
    expect(first).toHaveTextContent('Organizador');
    expect(second).toHaveTextContent('(você)');
    expect(second).not.toHaveTextContent('Organizador');
  });

  it('grupo inexistente ou de outros mostra "Grupo não encontrado" sem dados', async () => {
    server.use(http.get('/api/v1/groups/:id', () => problem(404, 'Grupo não encontrado.')));
    renderGroup('g-x');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Grupo não encontrado' }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Voltar para meus grupos' }));
    expect(screen.getByRole('heading', { name: 'Meus grupos' })).toBeInTheDocument();
  });

  it('outros erros mostram a mensagem', async () => {
    server.use(http.get('/api/v1/groups/:id', () => HttpResponse.error()));
    renderGroup();

    expect(
      await screen.findByText('Sem conexão. Verifique sua internet e tente novamente.'),
    ).toBeInTheDocument();
  });

  it('grupo sorteado mostra o selo "Sorteado" e esconde o código', async () => {
    mockGroup(openGroup({ status: 'DRAWN', drawnAt: '2026-12-20T15:00:00Z' }));
    renderGroup();

    expect(await screen.findByText('Sorteado')).toBeInTheDocument();
    expect(screen.queryByText('K7PQ-M2XA')).not.toBeInTheDocument();
  });

  // ---- confirmação (US2 da 002) ----

  /** Ana entrou pelo código e ainda não confirmou; Bruno é o organizador. */
  const ANA_PENDING = openGroup({
    organizer: false,
    myConfirmation: 'PENDING',
    confirmedCount: 1,
    drawBlockedReason: 'participants-pending',
    participants: [
      { userId: BRUNO_ID, name: 'Bruno', organizer: true, confirmed: true },
      { userId: ANA.id, name: 'Ana', organizer: false, confirmed: false },
    ],
  });

  it('a lista mostra quem confirmou, quem falta e "N de M confirmados"', async () => {
    mockGroup(ANA_PENDING);
    renderGroup();

    const list = await screen.findByRole('list', { name: 'Participantes' });
    const [bruno, ana] = within(list).getAllByRole('listitem');
    expect(within(bruno as HTMLElement).getByText('Confirmado')).toBeInTheDocument();
    expect(within(ana as HTMLElement).getByText('Pendente')).toBeInTheDocument();
    expect(screen.getByText('1 de 2 confirmados')).toBeInTheDocument();
  });

  it('o pendente vê o cartão para confirmar, e "Não vou participar" pede para sair', async () => {
    mockGroup(ANA_PENDING);
    renderGroup();

    const heading = await screen.findByRole('heading', { name: 'Confirme sua participação' });
    // Cartão de destaque, com o vidro da Glisse (feature 005)
    expect(heading.closest('.lg-glass')).not.toBeNull();
    expect(screen.getByText('Aguardando sorteio')).toHaveClass('rounded-control');
    expect(screen.getByRole('button', { name: 'Confirmar participação' })).toBeInTheDocument();
    expect(screen.queryByText('Você confirmou')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Não vou participar' }));
    expect(screen.getByRole('dialog', { name: 'Sair do grupo?' })).toBeInTheDocument();
  });

  it('confirmar mostra "Você confirmou" e atualiza a lista', async () => {
    const confirmed = openGroup({
      ...ANA_PENDING,
      myConfirmation: 'CONFIRMED',
      confirmedCount: 2,
      drawBlockedReason: 'not-enough-participants',
      participants: [
        { userId: BRUNO_ID, name: 'Bruno', organizer: true, confirmed: true },
        { userId: ANA.id, name: 'Ana', organizer: false, confirmed: true },
      ],
    });
    mockGroup(ANA_PENDING, confirmed);
    server.use(http.post('/api/v1/groups/g-1/confirmation', () => HttpResponse.json(confirmed)));
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar participação' }));

    expect(await screen.findByText('Você confirmou')).toBeInTheDocument();
    expect(screen.getByText('2 de 2 confirmados')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Confirmar participação' }),
    ).not.toBeInTheDocument();
  });

  it('o organizador não vê o cartão para confirmar', async () => {
    mockGroup(openGroup());
    renderGroup();

    await screen.findByRole('list', { name: 'Participantes' });
    expect(
      screen.queryByRole('button', { name: 'Confirmar participação' }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Você confirmou')).not.toBeInTheDocument();
  });

  it('confirmationsReset avisa quem não é organizador para conferir e confirmar de novo', async () => {
    const confirmed = openGroup({
      ...ANA_PENDING,
      myConfirmation: 'CONFIRMED',
      confirmedCount: 2,
    });
    mockGroup(confirmed, ANA_PENDING);
    renderGroup();
    expect(await screen.findByText('Você confirmou')).toBeInTheDocument();

    act(() =>
      FakeEventSource.latest().emit('group-updated', {
        status: 'OPEN',
        participantCount: 2,
        confirmationsReset: true,
      }),
    );

    expect(
      await screen.findByText('Os detalhes do evento mudaram. Confira e confirme de novo.'),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('button', { name: 'Confirmar participação' }),
    ).toBeInTheDocument();
  });

  it('o organizador não recebe o aviso de confirmar de novo', async () => {
    mockGroup(openGroup());
    renderGroup();
    await screen.findByRole('list', { name: 'Participantes' });

    act(() =>
      FakeEventSource.latest().emit('group-updated', {
        status: 'OPEN',
        participantCount: 2,
        confirmationsReset: true,
      }),
    );

    expect(
      screen.queryByText('Os detalhes do evento mudaram. Confira e confirme de novo.'),
    ).not.toBeInTheDocument();
  });

  it('ao mudar o local com o grupo aberto, o organizador é avisado antes de salvar', async () => {
    mockGroup(openGroup());
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Editar detalhes' }));
    const warning = 'Todos os participantes precisarão confirmar de novo.';
    expect(screen.queryByText(warning)).not.toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Descrição (opcional)'), 'Traga um prato');
    expect(screen.queryByText(warning)).not.toBeInTheDocument();

    const location = screen.getByLabelText('Local do evento');
    await userEvent.clear(location);
    await userEvent.type(location, 'Salão');
    expect(screen.getByText(warning)).toBeInTheDocument();
  });

  it('depois do sorteio, mudar o local não avisa sobre confirmações', async () => {
    mockGroup(openGroup({ status: 'DRAWN', drawnAt: '2026-12-01T12:00:00Z' }));
    server.use(
      http.get('/api/v1/groups/g-1/my-secret-friend', () => HttpResponse.json({ name: 'Bruno' })),
    );
    renderGroup();

    await userEvent.click(await screen.findByRole('button', { name: 'Editar detalhes' }));
    const location = screen.getByLabelText('Local do evento');
    await userEvent.clear(location);
    await userEvent.type(location, 'Salão');

    expect(
      screen.queryByText('Todos os participantes precisarão confirmar de novo.'),
    ).not.toBeInTheDocument();
  });
});
