import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { listGroups, type GroupSummary } from '../api/groups';
import { participantsLabel } from '../components/group/ParticipantList';
import { Badge } from '../components/ui/Badge';
import { Button, buttonClasses } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Spinner } from '../components/ui/Spinner';
import { GROUPS_KEY } from '../hooks/useGroup';
import { errorMessage } from '../lib/errors';
import { formatEventDate } from '../lib/eventDate';

function GroupCard({ group }: { group: GroupSummary }) {
  return (
    <li>
      <Link
        to={`/grupos/${group.id}`}
        className="block rounded-card focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <Card className="transition-shadow hover:shadow-md">
          <div className="flex items-start justify-between gap-3">
            <h2 className="min-w-0 text-lg font-semibold break-words text-text">{group.name}</h2>
            {group.organizer && <Badge tone="primary">Organizador</Badge>}
          </div>
          <p className="mt-1 text-sm text-text">
            {group.eventDate
              ? formatEventDate(group.eventDate, group.eventTime)
              : 'Data do evento a definir'}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-text-muted">
            <span>{participantsLabel(group.participantCount)}</span>
            <Badge tone={group.status === 'DRAWN' ? 'success' : 'neutral'}>
              {group.status === 'DRAWN' ? 'Sorteado' : 'Aguardando sorteio'}
            </Badge>
          </div>
        </Card>
      </Link>
    </li>
  );
}

/** / — "Meus grupos" (FR-016). */
export function HomePage() {
  const groups = useQuery({
    queryKey: GROUPS_KEY,
    queryFn: ({ signal }) => listGroups(signal),
  });

  return (
    <div className="py-6">
      <h1 className="text-2xl font-bold text-text">Meus grupos</h1>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <Link to="/grupos/novo" className={buttonClasses('primary', true)}>
          Criar grupo
        </Link>
        <Link to="/entrar-no-grupo" className={buttonClasses('secondary', true)}>
          Entrar com código
        </Link>
      </div>

      <div className="mt-6">
        {groups.isPending && (
          <div className="flex justify-center py-10 text-primary">
            <Spinner label="Carregando grupos" className="size-8" />
          </div>
        )}
        {groups.isError && (
          <Card>
            <p role="alert" className="text-danger">
              {errorMessage(groups.error)}
            </p>
            <Button variant="secondary" className="mt-4" onClick={() => void groups.refetch()}>
              Tentar de novo
            </Button>
          </Card>
        )}
        {groups.isSuccess && groups.data.length === 0 && (
          <Card className="text-center">
            <p className="font-medium text-text">Você ainda não participa de nenhum grupo.</p>
            <p className="mt-1 text-sm text-text-muted">
              Crie um grupo ou entre com o código que recebeu.
            </p>
          </Card>
        )}
        {groups.isSuccess && groups.data.length > 0 && (
          <ul className="flex flex-col gap-3">
            {groups.data.map((group) => (
              <GroupCard key={group.id} group={group} />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
