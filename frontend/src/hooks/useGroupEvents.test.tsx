import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FakeEventSource } from '../test/eventSource';
import { GROUPS_KEY, groupKey } from './useGroup';
import { POLL_INTERVAL_MS, useGroupEvents } from './useGroupEvents';

/** `null` = sem id (um `undefined` acionaria o valor padrão). */
function setup(groupId: string | null = 'g-1') {
  const queryClient = new QueryClient();
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  const hook = renderHook(() => useGroupEvents(groupId ?? undefined), { wrapper });
  return { invalidate, hook };
}

describe('useGroupEvents', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('abre o EventSource do grupo', () => {
    setup('g-1');

    expect(FakeEventSource.latest().url).toBe('/api/v1/groups/g-1/events');
  });

  it('participants-changed e group-drawn invalidam a consulta do grupo', () => {
    const { invalidate } = setup();

    act(() => FakeEventSource.latest().emit('participants-changed', { participantCount: 3 }));
    act(() => FakeEventSource.latest().emit('group-drawn', { status: 'DRAWN' }));

    expect(invalidate).toHaveBeenCalledTimes(2);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: groupKey('g-1') });
  });

  it('group-updated invalida a consulta e avisa quando as confirmações voltaram a pendente', () => {
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
    const onConfirmationsReset = vi.fn();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    renderHook(() => useGroupEvents('g-1', { onConfirmationsReset }), { wrapper });

    act(() =>
      FakeEventSource.latest().emit('group-updated', {
        status: 'OPEN',
        participantCount: 3,
        confirmationsReset: false,
      }),
    );
    expect(invalidate).toHaveBeenCalledWith({ queryKey: groupKey('g-1') });
    expect(onConfirmationsReset).not.toHaveBeenCalled();

    act(() =>
      FakeEventSource.latest().emit('group-updated', {
        status: 'OPEN',
        participantCount: 3,
        confirmationsReset: true,
      }),
    );
    expect(onConfirmationsReset).toHaveBeenCalledTimes(1);
  });

  it('group-finished invalida o grupo e a lista "Meus grupos" e avisa', () => {
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
    const onFinished = vi.fn();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    renderHook(() => useGroupEvents('g-1', { onFinished }), { wrapper });

    act(() =>
      FakeEventSource.latest().emit('group-finished', { status: 'FINISHED', participantCount: 3 }),
    );

    expect(invalidate).toHaveBeenCalledWith({ queryKey: groupKey('g-1') });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: GROUPS_KEY });
    expect(onFinished).toHaveBeenCalledTimes(1);
  });

  it('em erro refaz a consulta e passa a consultar a cada 10 s até reconectar', () => {
    const { invalidate } = setup();
    const source = FakeEventSource.latest();

    act(() => source.fail());
    expect(invalidate).toHaveBeenCalledTimes(1);

    act(() => vi.advanceTimersByTime(POLL_INTERVAL_MS * 2));
    expect(invalidate).toHaveBeenCalledTimes(3);
    expect(POLL_INTERVAL_MS).toBe(10_000);

    // Reconectou: uma última atualização e o polling para
    act(() => source.open());
    expect(invalidate).toHaveBeenCalledTimes(4);
    act(() => vi.advanceTimersByTime(POLL_INTERVAL_MS * 3));
    expect(invalidate).toHaveBeenCalledTimes(4);
  });

  it('a primeira conexão não refaz a consulta', () => {
    const { invalidate } = setup();

    act(() => FakeEventSource.latest().open());

    expect(invalidate).not.toHaveBeenCalled();
  });

  it('fecha a conexão e para o polling ao desmontar', () => {
    const { invalidate, hook } = setup();
    const source = FakeEventSource.latest();
    act(() => source.fail());

    hook.unmount();
    act(() => vi.advanceTimersByTime(POLL_INTERVAL_MS * 3));

    expect(source.closed).toBe(true);
    expect(invalidate).toHaveBeenCalledTimes(1);
  });

  it('sem id não abre conexão', () => {
    setup(null);

    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it('sem suporte a EventSource, usa só o polling', () => {
    vi.stubGlobal('EventSource', undefined);
    try {
      const { invalidate } = setup();
      act(() => vi.advanceTimersByTime(POLL_INTERVAL_MS));
      expect(invalidate).toHaveBeenCalledTimes(1);
    } finally {
      vi.stubGlobal('EventSource', FakeEventSource);
    }
  });
});
