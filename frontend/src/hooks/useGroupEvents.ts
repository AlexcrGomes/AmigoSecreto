import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { groupKey } from './useGroup';

/** Consulta de contingência enquanto o SSE estiver indisponível (contracts/sse-events.md). */
export const POLL_INTERVAL_MS = 10_000;

/**
 * Sinais em tempo real do grupo. Os eventos não trazem dados: cada um só manda refazer a consulta do
 * grupo. Sem conexão (erro ou navegador sem SSE), consulta a cada 10 s até reconectar.
 */
export function useGroupEvents(groupId: string | undefined) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!groupId) return;
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: groupKey(groupId) });
    };

    let pollTimer: number | undefined;
    const startPolling = () => {
      pollTimer ??= window.setInterval(refresh, POLL_INTERVAL_MS);
    };
    const stopPolling = () => {
      window.clearInterval(pollTimer);
      pollTimer = undefined;
    };

    if (typeof EventSource === 'undefined') {
      startPolling();
      return stopPolling;
    }

    const source = new EventSource(`/api/v1/groups/${encodeURIComponent(groupId)}/events`);
    let disconnected = false;
    source.addEventListener('participants-changed', refresh);
    source.addEventListener('group-drawn', refresh);
    source.onopen = () => {
      if (disconnected) {
        disconnected = false;
        stopPolling();
        refresh();
      }
    };
    source.onerror = () => {
      if (!disconnected) {
        disconnected = true;
        refresh();
      }
      startPolling();
    };

    return () => {
      stopPolling();
      source.close();
    };
  }, [groupId, queryClient]);
}
