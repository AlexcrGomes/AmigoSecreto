import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { groupKey } from './useGroup';

/** Consulta de contingência enquanto o SSE estiver indisponível (contracts/sse-events.md). */
export const POLL_INTERVAL_MS = 10_000;

interface GroupEventHandlers {
  /** O sorteio acabou de acontecer (evento `group-drawn`). */
  onDrawn?: () => void;
  /** O próprio usuário foi removido do grupo (evento `membership-revoked`). */
  onRevoked?: () => void;
}

/**
 * Sinais em tempo real do grupo. Os eventos não trazem dados: cada um só manda refazer a consulta do
 * grupo. Sem conexão (erro ou navegador sem SSE), consulta a cada 10 s até reconectar.
 */
export function useGroupEvents(groupId: string | undefined, handlers: GroupEventHandlers = {}) {
  const queryClient = useQueryClient();
  const handlersRef = useRef(handlers);

  useEffect(() => {
    handlersRef.current = handlers;
  });

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
    source.addEventListener('group-drawn', () => {
      refresh();
      handlersRef.current.onDrawn?.();
    });
    source.addEventListener('membership-revoked', () => {
      // Deixou de ser membro: nada mais a ouvir (nem reconectar, nem consultar)
      stopPolling();
      source.close();
      handlersRef.current.onRevoked?.();
    });
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
