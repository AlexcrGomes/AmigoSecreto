import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { acknowledgeExitRequestNotice, listExitRequestNotices } from '../api/exitRequests';

export const EXIT_NOTICES_KEY = ['exit-request-notices'] as const;

/**
 * Avisos das decisões sobre os pedidos de saída do usuário da sessão (FR-026, research R20). Quem estava
 * fora vê a decisão na próxima entrada; `acknowledge` marca como visto e recarrega a lista.
 */
export function useExitRequestNotices() {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: EXIT_NOTICES_KEY,
    queryFn: ({ signal }) => listExitRequestNotices(signal),
  });

  const acknowledge = useCallback(
    async (requestId: string) => {
      await acknowledgeExitRequestNotice(requestId);
      await queryClient.invalidateQueries({ queryKey: EXIT_NOTICES_KEY });
    },
    [queryClient],
  );

  return { notices: query.data ?? [], acknowledge };
}
