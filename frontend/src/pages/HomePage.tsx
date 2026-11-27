import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { listGroups, type GroupStatus, type GroupSummary } from '../api/groups';
import { participantsLabel } from '../components/group/ParticipantList';
import { Badge } from '../components/ui/Badge';
import { Button, buttonClasses } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Spinner } from '../components/ui/Spinner';
import { IconPlus, IconTicket } from '../components/ui/icons';
import { GROUPS_KEY } from '../hooks/useGroup';
import { useExitRequestNotices } from '../hooks/useExitRequestNotices';
import { errorMessage } from '../lib/errors';
import { formatEventDate } from '../lib/eventDate';

const STATUS_LABEL: Record<GroupStatus, string> = {
  OPEN: 'Aguardando sorteio',
  DRAWN: 'Sorteado',
  FINISHED: 'Finalizado',
};

function GroupCard({ group }: { group: GroupSummary }) {
  return (
    <li>
      <Link
        to={`/grupos/${group.id}`}
        className="group block h-full rounded-card focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <Card className="group-card flex h-full flex-col transition-transform group-hover:-translate-y-0.5">
          <div className="flex items-start justify-between gap-3">
            <h3 className="min-w-0 text-lg font-semibold break-words text-text">{group.name}</h3>
            {group.organizer && <Badge tone="primary">Organizador</Badge>}
          </div>
          <p className="mt-2 flex items-center gap-1.5 text-sm text-text">
            {group.eventDate
              ? formatEventDate(group.eventDate, group.eventTime)
              : 'Data do evento a definir'}
          </p>
          <div className="mt-auto flex flex-wrap items-center justify-between gap-2 pt-4 text-sm text-text-muted">
            <span>{participantsLabel(group.participantCount)}</span>
            <Badge tone={group.status === 'DRAWN' ? 'success' : 'neutral'}>
              {STATUS_LABEL[group.status]}
            </Badge>
          </div>
        </Card>
      </Link>
    </li>
  );
}

/** Uma seção da tela inicial; some quando não tem grupos. */
function GroupSection({
  id,
  title,
  groups,
}: {
  id: string;
  title: string;
  groups: GroupSummary[];
}) {
  if (groups.length === 0) return null;
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="mb-3 text-sm font-bold tracking-wider text-accent-gold uppercase">
        {title}
      </h2>
      <ul className="grid gap-3 md:grid-cols-2 md:gap-4 xl:grid-cols-3">
        {groups.map((group) => (
          <GroupCard key={group.id} group={group} />
        ))}
      </ul>
    </section>
  );
}

/** / — "Meus grupos" (FR-016), com os finalizados em seção própria (FR-020 da 002). */
export function HomePage() {
  const groups = useQuery({
    queryKey: GROUPS_KEY,
    queryFn: ({ signal }) => listGroups(signal),
  });
  const exitNotices = useExitRequestNotices();
  // Saídas aprovadas: o grupo sumiu da lista, então o aviso fica aqui até ser visto (FR-026)
  const approvals = exitNotices.notices.filter((notice) => notice.status === 'APPROVED');

  return (
    <div className="py-6 md:py-2">
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-text">Meus grupos</h1>
          <span aria-hidden="true" className="mt-2 block h-1 w-12 rounded-full bg-gold-soft" />
        </div>
        <div className="grid grid-cols-2 gap-3 md:flex">
          <Link
            to="/grupos/novo"
            className={`${buttonClasses('primary', true)} text-sm md:w-auto md:px-6 md:text-base`}
          >
            <IconPlus className="hidden size-5 min-[400px]:block" />
            Criar grupo
          </Link>
          <Link
            to="/entrar-no-grupo"
            className={`${buttonClasses('secondary', true)} text-sm whitespace-nowrap md:w-auto md:px-6 md:text-base`}
          >
            <IconTicket className="hidden size-5 text-accent-gold md:block" />
            Entrar com código
          </Link>
        </div>
      </div>

      {approvals.length > 0 && (
        <div className="mt-6 flex flex-col gap-3">
          {approvals.map((notice) => (
            <Card key={notice.id} highlight>
              {notice.subjectName ? (
                <p role="status" className="text-text">
                  {notice.subjectName} saiu do grupo{' '}
                  <span className="font-semibold">{notice.groupName}</span>. O organizador aprovou o
                  pedido de saída.
                </p>
              ) : (
                <p role="status" className="text-text">
                  Você saiu do grupo <span className="font-semibold">{notice.groupName}</span>. O
                  organizador aprovou seu pedido de saída.
                </p>
              )}
              <Button
                variant="secondary"
                fullWidth
                className="mt-3"
                onClick={() => void exitNotices.acknowledge(notice.id)}
              >
                Entendi
              </Button>
            </Card>
          ))}
        </div>
      )}

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
          <Card className="mx-auto max-w-xl py-10 text-center">
            <p className="font-medium text-text">Você ainda não participa de nenhum grupo.</p>
            <p className="mt-1 text-sm text-text-muted">
              Crie um grupo ou entre com o código que recebeu.
            </p>
          </Card>
        )}
        {groups.isSuccess && groups.data.length > 0 && (
          <div className="flex flex-col gap-8">
            <GroupSection
              id="grupos-em-andamento"
              title="Em andamento"
              groups={groups.data.filter((group) => group.status !== 'FINISHED')}
            />
            <GroupSection
              id="grupos-finalizados"
              title="Finalizados"
              groups={groups.data.filter((group) => group.status === 'FINISHED')}
            />
          </div>
        )}
      </div>
    </div>
  );
}
