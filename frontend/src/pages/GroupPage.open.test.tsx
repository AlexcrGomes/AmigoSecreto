import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FakeEventSource } from '../test/eventSource';
import { BRUNO_ID, mockGroup, openGroup, problem } from '../test/groups';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { GroupPage } from './GroupPage';

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
});
