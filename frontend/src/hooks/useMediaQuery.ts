import { useCallback, useSyncExternalStore } from 'react';

/**
 * Se a consulta de mídia casa agora, atualizando quando muda — ex.: `(min-width: 768px)` para a
 * navegação (feature 005, research R49) ou `(prefers-reduced-motion: reduce)`.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (notify: () => void) => {
      const list = window.matchMedia(query);
      list.addEventListener('change', notify);
      return () => list.removeEventListener('change', notify);
    },
    [query],
  );
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(query).matches,
    () => false,
  );
}
