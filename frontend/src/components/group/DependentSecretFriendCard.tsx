import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getDependentsSecretFriends } from '../../api/dependents';
import type { DependentSecretFriend, MyDependent } from '../../api/groups';
import { errorMessage } from '../../lib/errors';
import { formatGiftValue } from '../../lib/giftValue';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { Spinner } from '../ui/Spinner';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { revealedNameClasses } from './SecretFriendCard';
import { WishlistView } from './WishlistView';

export function dependentsSecretFriendsKey(groupId: string) {
  return ['dependents-secret-friends', groupId] as const;
}

/**
 * Um cartão "Amigo secreto de Lucas" para cada dependente do usuário da sessão (US2 da 004). Cada cartão tem o
 * próprio Revelar/Ocultar; os resultados vêm de uma rota sem id (research R38), buscada no primeiro "Revelar" e
 * descartada da memória quando todos os cartões ficam ocultos. Nada vai para localStorage ou sessionStorage.
 */
export function DependentSecretFriendCards({
  groupId,
  dependents,
  giftValue = null,
}: {
  groupId: string;
  dependents: MyDependent[];
  giftValue?: number | null;
}) {
  const queryClient = useQueryClient();
  const [revealed, setRevealed] = useState<ReadonlySet<string>>(new Set());
  const results = useQuery({
    queryKey: dependentsSecretFriendsKey(groupId),
    queryFn: ({ signal }) => getDependentsSecretFriends(groupId, signal),
    enabled: revealed.size > 0,
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });

  if (dependents.length === 0) return null;

  function reveal(dependentId: string) {
    if (revealed.has(dependentId) && results.isError) {
      void results.refetch();
      return;
    }
    setRevealed((current) => new Set(current).add(dependentId));
  }

  function hide(dependentId: string) {
    const next = new Set(revealed);
    next.delete(dependentId);
    setRevealed(next);
    if (next.size === 0) {
      queryClient.removeQueries({ queryKey: dependentsSecretFriendsKey(groupId) });
    }
  }

  return (
    <>
      {dependents.map((dependent) => (
        <DependentSecretFriendCard
          key={dependent.id}
          name={dependent.name}
          giftValue={giftValue}
          revealed={revealed.has(dependent.id)}
          loading={revealed.has(dependent.id) && results.isFetching}
          error={revealed.has(dependent.id) && results.isError ? errorMessage(results.error) : null}
          result={
            revealed.has(dependent.id) && results.isSuccess
              ? results.data.find((item) => item.dependentId === dependent.id)
              : undefined
          }
          onReveal={() => reveal(dependent.id)}
          onHide={() => hide(dependent.id)}
        />
      ))}
    </>
  );
}

interface DependentSecretFriendCardProps {
  name: string;
  giftValue: number | null;
  revealed: boolean;
  loading: boolean;
  error: string | null;
  result: DependentSecretFriend | undefined;
  onReveal: () => void;
  onHide: () => void;
}

function DependentSecretFriendCard({
  name,
  giftValue,
  revealed,
  loading,
  error,
  result,
  onReveal,
  onHide,
}: DependentSecretFriendCardProps) {
  const friend = revealed ? result?.secretFriend : undefined;
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)');

  return (
    <Card highlight className="text-center">
      <h2 className="text-lg font-semibold break-words text-text">Amigo secreto de {name}</h2>
      <div className="my-4 flex min-h-12 items-center justify-center">
        {loading ? (
          <Spinner label="Revelando" className="size-8 text-primary" />
        ) : friend ? (
          <p className={revealedNameClasses(reducedMotion)}>{friend.name}</p>
        ) : (
          <p
            className="text-3xl font-bold tracking-widest text-text-muted"
            aria-label="Nome oculto"
          >
            ••••••
          </p>
        )}
      </div>
      {friend && giftValue != null && (
        <p className="mb-2 text-sm text-text-muted">
          Valor do presente:{' '}
          <span className="font-medium text-text">{formatGiftValue(giftValue)}</span>
        </p>
      )}
      {friend && (
        <WishlistView
          wishlist={friend.wishlist ?? []}
          updatedAt={friend.wishlistUpdatedAt ?? null}
        />
      )}
      {error && (
        <p role="alert" className="mb-4 text-danger">
          {error}
        </p>
      )}
      {friend ? (
        <Button
          variant="secondary"
          fullWidth
          aria-label={`Ocultar o amigo secreto de ${name}`}
          onClick={onHide}
        >
          Ocultar
        </Button>
      ) : (
        <Button
          fullWidth
          loading={loading}
          aria-label={`Revelar o amigo secreto de ${name}`}
          onClick={onReveal}
        >
          Revelar
        </Button>
      )}
      <p className="mt-3 text-sm text-text-muted">Só você vê quem {name} tirou.</p>
    </Card>
  );
}
