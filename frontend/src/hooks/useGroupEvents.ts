import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { dependentsSecretFriendsKey } from '../components/group/DependentSecretFriendCard';
import { secretFriendKey } from '../components/group/SecretFriendCard';
import { EXIT_NOTICES_KEY } from './useExitRequestNotices';
import { GROUPS_KEY, groupKey } from './useGroup';

/** Consulta de contingência enquanto o SSE estiver indisponível (contracts/sse-events.md). */
export const POLL_INTERVAL_MS = 10_000;

interface GroupEventHandlers {
  /** O sorteio acabou de acontecer (evento `group-drawn`). */
  onDrawn?: () => void;
  /**
   * O próprio usuário deixou o grupo (evento `membership-revoked`): `removed` pelo organizador ou
   * `exit-approved` (pedido de saída aprovado).
   */
  onRevoked?: (reason: string | null) => void;
  /** Uma saída foi aprovada e o sorteio foi desfeito (evento `draw-undone`). */
  onDrawUndone?: () => void;
  /** Data, horário ou local mudaram e as confirmações voltaram a pendente (`group-updated`). */
  onConfirmationsReset?: () => void;
  /** O evento aconteceu e o grupo foi finalizado (evento `group-finished`). */
  onFinished?: () => void;
}

/** Corpo JSON do sinal; um corpo inválido vira `{}`. */
function signalData(event: Event): Record<string, unknown> {
  if (!(event instanceof MessageEvent) || typeof event.data !== 'string') return {};
  try {
    const data: unknown = JSON.parse(event.data);
    return typeof data === 'object' && data !== null ? (data as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

/** Lê `confirmationsReset` do sinal; um corpo inválido conta como `false`. */
function confirmationsReset(event: Event): boolean {
  return signalData(event).confirmationsReset === true;
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
    // Recusas e cancelamentos de pedidos de saída aparecem ao vivo (FR-026)
    const refreshNotices = () => {
      void queryClient.invalidateQueries({ queryKey: EXIT_NOTICES_KEY });
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
    // Detalhes do evento, confirmações ou pedidos de saída mudaram (contracts/sse-events.md da 002)
    source.addEventListener('group-updated', (event) => {
      refresh();
      refreshNotices();
      if (confirmationsReset(event)) handlersRef.current.onConfirmationsReset?.();
    });
    // Saída aprovada: o nome revelado sai da memória antes de qualquer outra coisa (FR-028)
    source.addEventListener('draw-undone', () => {
      queryClient.removeQueries({ queryKey: secretFriendKey(groupId) });
      // Os resultados dos dependentes também (feature 004)
      queryClient.removeQueries({ queryKey: dependentsSecretFriendsKey(groupId) });
      refresh();
      refreshNotices();
      handlersRef.current.onDrawUndone?.();
    });
    source.addEventListener('group-drawn', () => {
      refresh();
      handlersRef.current.onDrawn?.();
    });
    // O grupo muda de seção na tela inicial: a lista também precisa ser refeita
    source.addEventListener('group-finished', () => {
      refresh();
      refreshNotices();
      void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
      handlersRef.current.onFinished?.();
    });
    source.addEventListener('membership-revoked', (event) => {
      // Deixou de ser membro: nada mais a ouvir (nem reconectar, nem consultar)
      stopPolling();
      source.close();
      const reason = signalData(event).reason;
      handlersRef.current.onRevoked?.(typeof reason === 'string' ? reason : null);
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
