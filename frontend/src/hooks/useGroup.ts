import { useQuery } from '@tanstack/react-query';
import { getGroup } from '../api/groups';

export const GROUPS_KEY = ['groups'] as const;

export function groupKey(id: string) {
  return ['group', id] as const;
}

/** Detalhes do grupo; atualizado pelos sinais do SSE (useGroupEvents). */
export function useGroup(id: string | undefined) {
  return useQuery({
    queryKey: groupKey(id ?? ''),
    queryFn: ({ signal }) => getGroup(id ?? '', signal),
    enabled: Boolean(id),
  });
}
