import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getMySecretFriend } from '../../api/groups';
import { errorMessage } from '../../lib/errors';
import { formatGiftValue } from '../../lib/giftValue';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { Spinner } from '../ui/Spinner';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { WishlistView } from './WishlistView';

/** Nome revelado em destaque; a entrada animada (até 300 ms) some com "reduzir movimento" (R50). */
export function revealedNameClasses(reducedMotion: boolean): string {
  const base = 'text-3xl font-extrabold break-words text-primary';
  return reducedMotion ? base : `${base} reveal-in`;
}

export function secretFriendKey(groupId: string) {
  return ['secret-friend', groupId] as const;
}

/**
 * "Seu amigo secreto" (FR-028 a FR-031): o nome — e, na feature 003, a lista de desejos da pessoa — fica oculto
 * até tocar em "Revelar" e some de novo em
 * "Ocultar". Nunca vai para cache persistente, localStorage ou sessionStorage — e é descartado da
 * memória ao ocultar.
 */
export function SecretFriendCard({
  groupId,
  giftValue = null,
}: {
  groupId: string;
  /** Valor do presente do grupo, mostrado junto da lista (feature 003, FR-021). */
  giftValue?: number | null;
}) {
  const queryClient = useQueryClient();
  const [revealed, setRevealed] = useState(false);
  const friend = useQuery({
    queryKey: secretFriendKey(groupId),
    queryFn: ({ signal }) => getMySecretFriend(groupId, signal),
    enabled: revealed,
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });

  function hide() {
    setRevealed(false);
    queryClient.removeQueries({ queryKey: secretFriendKey(groupId) });
  }

  const showName = revealed && friend.isSuccess;
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)');

  return (
    <Card highlight className="text-center">
      <h2 className="text-lg font-semibold text-text">Seu amigo secreto</h2>
      <div className="my-4 flex min-h-12 items-center justify-center">
        {revealed && friend.isFetching ? (
          <Spinner label="Revelando" className="size-8 text-primary" />
        ) : showName ? (
          <p className={revealedNameClasses(reducedMotion)}>{friend.data.name}</p>
        ) : (
          <p
            className="text-3xl font-bold tracking-widest text-text-muted"
            aria-label="Nome oculto"
          >
            ••••••
          </p>
        )}
      </div>
      {showName && giftValue != null && (
        <p className="mb-2 text-sm text-text-muted">
          Valor do presente:{' '}
          <span className="font-medium text-text">{formatGiftValue(giftValue)}</span>
        </p>
      )}
      {showName && (
        <WishlistView
          wishlist={friend.data.wishlist ?? []}
          updatedAt={friend.data.wishlistUpdatedAt ?? null}
        />
      )}
      {revealed && friend.isError && (
        <p role="alert" className="mb-4 text-danger">
          {errorMessage(friend.error)}
        </p>
      )}
      {showName ? (
        <Button variant="secondary" fullWidth onClick={hide}>
          Ocultar
        </Button>
      ) : (
        <Button
          fullWidth
          loading={revealed && friend.isFetching}
          onClick={() => {
            if (revealed) void friend.refetch();
            else setRevealed(true);
          }}
        >
          Revelar
        </Button>
      )}
      <p className="mt-3 text-sm text-text-muted">Só você vê este nome.</p>
    </Card>
  );
}
