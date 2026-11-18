import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthProvider';
import { DrawButton } from '../components/group/DrawButton';
import { ParticipantList } from '../components/group/ParticipantList';
import { SecretFriendCard } from '../components/group/SecretFriendCard';
import { ShareCode } from '../components/group/ShareCode';
import { Badge } from '../components/ui/Badge';
import { buttonClasses } from '../components/ui/Button';
import { Spinner } from '../components/ui/Spinner';
import { useGroup } from '../hooks/useGroup';
import { useGroupEvents } from '../hooks/useGroupEvents';
import { errorMessage } from '../lib/errors';

const drawnDate = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });

/**
 * /grupos/:id (contracts/ui-routes.md). OPEN: código, lista ao vivo e "Realizar sorteio" para o
 * organizador. DRAWN: selo com a data, "Seu amigo secreto" e a lista só com nomes.
 */
export function GroupPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const group = useGroup(id);
  const [justDrawn, setJustDrawn] = useState(false);
  useGroupEvents(id, { onDrawn: () => setJustDrawn(true) });

  if (group.isPending) {
    return (
      <div className="flex justify-center py-16 text-primary">
        <Spinner label="Carregando grupo" className="size-8" />
      </div>
    );
  }

  if (group.isError) {
    const notFound = group.error instanceof ApiError && group.error.status === 404;
    return (
      <div className="py-6">
        <h1 className="text-2xl font-bold text-text">
          {notFound ? 'Grupo não encontrado' : 'Não foi possível carregar o grupo'}
        </h1>
        <p className="mt-2 text-text-muted">
          {notFound ? 'Ele não existe ou você não participa dele.' : errorMessage(group.error)}
        </p>
        <Link to="/" className={`${buttonClasses('secondary')} mt-6`}>
          Voltar para meus grupos
        </Link>
      </div>
    );
  }

  const detail = group.data;
  const drawn = detail.status === 'DRAWN';

  return (
    <div className="flex flex-col gap-4 py-6">
      <div>
        <h1 className="text-2xl font-bold break-words text-text">{detail.name}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Badge tone={drawn ? 'success' : 'primary'}>
            {drawn ? 'Sorteado' : 'Aguardando sorteio'}
          </Badge>
          {drawn && detail.drawnAt && (
            <span className="text-sm text-text-muted">
              Sorteio realizado em {drawnDate.format(new Date(detail.drawnAt))}
            </span>
          )}
        </div>
      </div>

      {drawn && justDrawn && (
        <p
          role="status"
          className="rounded-control border border-success bg-surface px-4 py-3 font-medium text-success"
        >
          O sorteio foi realizado! Toque em Revelar para ver quem você tirou.
        </p>
      )}

      {drawn ? (
        <SecretFriendCard groupId={detail.id} />
      ) : (
        <>
          <ShareCode code={detail.code} groupName={detail.name} />
          <DrawButton group={detail} />
        </>
      )}

      <ParticipantList participants={detail.participants} currentUserId={user?.id} />
    </div>
  );
}
