import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../api/client';
import {
  leaveGroup,
  removeParticipant,
  updateEventDetails,
  type EventDetailsBody,
  type Participant,
} from '../api/groups';
import { useAuth } from '../auth/AuthProvider';
import { ConfirmParticipationCard } from '../components/group/ConfirmParticipationCard';
import { DrawButton } from '../components/group/DrawButton';
import { EventDetailsCard } from '../components/group/EventDetailsCard';
import { EventDetailsForm } from '../components/group/EventDetailsForm';
import { ParticipantList } from '../components/group/ParticipantList';
import { SecretFriendCard } from '../components/group/SecretFriendCard';
import { ShareCode } from '../components/group/ShareCode';
import { Badge } from '../components/ui/Badge';
import { Button, buttonClasses } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { ConfirmModal } from '../components/ui/ConfirmModal';
import { Modal } from '../components/ui/Modal';
import { Spinner } from '../components/ui/Spinner';
import { useToast } from '../components/ui/Toast';
import { GROUPS_KEY, groupKey, useGroup } from '../hooks/useGroup';
import { useGroupEvents } from '../hooks/useGroupEvents';
import { CONFIRMATIONS_RESET, errorMessage } from '../lib/errors';

const drawnDate = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });

/**
 * /grupos/:id (contracts/ui-routes.md). OPEN: código, lista ao vivo com quem confirmou, "Realizar
 * sorteio" e "Remover" para o organizador, "Confirmar participação" para quem está pendente e "Sair do
 * grupo" para os demais. DRAWN: selo com a data, "Seu amigo secreto" e a
 * lista só com nomes. FINISHED: o mesmo, somente leitura (FR-018, FR-019).
 */
export function GroupPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const group = useGroup(id);
  const [justDrawn, setJustDrawn] = useState(false);
  const [removing, setRemoving] = useState<Participant | null>(null);
  const [leaving, setLeaving] = useState(false);
  const [editingDetails, setEditingDetails] = useState(false);
  const [confirmationsReset, setConfirmationsReset] = useState(false);
  const [justFinished, setJustFinished] = useState(false);

  /** Saiu do grupo (por conta própria ou removido): descarta os dados e volta para a lista. */
  function exitGroup(message: string, kind: 'success' | 'error') {
    if (id) queryClient.removeQueries({ queryKey: groupKey(id) });
    void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
    toast.show(message, kind);
    navigate('/', { replace: true });
  }

  useGroupEvents(id, {
    onDrawn: () => setJustDrawn(true),
    onRevoked: () => exitGroup('Você foi removido deste grupo', 'error'),
    // O organizador mudou data, horário ou local: quem não é organizador precisa confirmar de novo
    onConfirmationsReset: () => {
      if (group.data && !group.data.organizer) setConfirmationsReset(true);
    },
    onFinished: () => setJustFinished(true),
  });

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
  const open = detail.status === 'OPEN';
  const finished = detail.status === 'FINISHED';
  const pending = open && detail.myConfirmation === 'PENDING' && !detail.eventDetailsMissing;
  const confirmedGuest = open && !detail.organizer && detail.myConfirmation === 'CONFIRMED';

  async function confirmRemove() {
    if (!removing) return;
    await removeParticipant(detail.id, removing.userId);
    toast.show(`${removing.name} não participa mais do grupo.`, 'success');
    setRemoving(null);
    await queryClient.invalidateQueries({ queryKey: groupKey(detail.id) });
  }

  async function confirmLeave() {
    await leaveGroup(detail.id);
    exitGroup('Você saiu do grupo.', 'success');
  }

  /** Organizador, com o grupo aberto ou sorteado (FR-005, FR-006). */
  const canEditDetails =
    detail.organizer && (detail.status === 'OPEN' || detail.status === 'DRAWN');

  async function saveDetails(details: EventDetailsBody) {
    const updated = await updateEventDetails(detail.id, details);
    queryClient.setQueryData(groupKey(detail.id), updated);
    void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
    setEditingDetails(false);
    toast.show('Detalhes do evento atualizados.', 'success');
  }

  return (
    <div className="flex flex-col gap-4 py-6">
      <div>
        <h1 className="text-2xl font-bold break-words text-text">{detail.name}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Badge tone={drawn ? 'success' : finished ? 'neutral' : 'primary'}>
            {drawn ? 'Sorteado' : finished ? 'Finalizado' : 'Aguardando sorteio'}
          </Badge>
          {confirmedGuest && <Badge tone="success">Você confirmou</Badge>}
          {drawn && detail.drawnAt && (
            <span className="text-sm text-text-muted">
              Sorteio realizado em {drawnDate.format(new Date(detail.drawnAt))}
            </span>
          )}
          {finished && detail.finishedAt && (
            <span className="text-sm text-text-muted">
              Finalizado em {drawnDate.format(new Date(detail.finishedAt))}
            </span>
          )}
        </div>
      </div>

      {detail.eventDetails ? (
        <EventDetailsCard
          details={detail.eventDetails}
          onEdit={canEditDetails ? () => setEditingDetails(true) : undefined}
        />
      ) : canEditDetails ? (
        <Card className="border border-primary">
          <p className="font-semibold text-text">Informe a data e o local do evento</p>
          <p className="mt-1 text-sm text-text-muted">
            Este grupo foi criado antes de existirem os detalhes do evento.
          </p>
          <Button fullWidth className="mt-4" onClick={() => setEditingDetails(true)}>
            Informar detalhes
          </Button>
        </Card>
      ) : (
        <p className="text-text-muted">
          O organizador ainda vai informar a data e o local do evento.
        </p>
      )}

      {pending && (
        <ConfirmParticipationCard
          group={detail}
          onLeave={() => setLeaving(true)}
          notice={confirmationsReset ? CONFIRMATIONS_RESET : null}
        />
      )}

      {drawn && justDrawn && (
        <p
          role="status"
          className="rounded-control border border-success bg-surface px-4 py-3 font-medium text-success"
        >
          O sorteio foi realizado! Toque em Revelar para ver quem você tirou.
        </p>
      )}

      {finished && justFinished && (
        <p
          role="status"
          className="rounded-control border border-border bg-surface px-4 py-3 font-medium text-text"
        >
          O evento aconteceu. Este grupo foi finalizado.
        </p>
      )}

      {open ? (
        <>
          <ShareCode code={detail.code} groupName={detail.name} />
          <DrawButton group={detail} />
        </>
      ) : (
        <SecretFriendCard groupId={detail.id} />
      )}

      <ParticipantList
        participants={detail.participants}
        currentUserId={user?.id}
        onRemove={open && detail.organizer ? setRemoving : undefined}
        showConfirmation={open}
      />

      {/* O pendente já tem "Não vou participar" no cartão de confirmação */}
      {open && !detail.organizer && !pending && (
        <Button variant="danger" fullWidth onClick={() => setLeaving(true)}>
          Sair do grupo
        </Button>
      )}

      <Modal
        open={editingDetails}
        title={detail.eventDetails ? 'Editar detalhes do evento' : 'Detalhes do evento'}
        onClose={() => setEditingDetails(false)}
      >
        <EventDetailsForm
          initial={detail.eventDetails}
          submitLabel="Salvar"
          onSubmit={saveDetails}
          scheduleChangeWarning={
            open ? 'Todos os participantes precisarão confirmar de novo.' : undefined
          }
          footer={
            <Button variant="ghost" fullWidth onClick={() => setEditingDetails(false)}>
              Cancelar
            </Button>
          }
        />
      </Modal>

      <ConfirmModal
        open={removing !== null}
        title="Remover participante?"
        confirmLabel="Remover"
        onConfirm={confirmRemove}
        onClose={() => setRemoving(null)}
      >
        <p>
          {removing?.name} sairá do grupo e será avisado. Enquanto não houver sorteio, poderá entrar
          de novo com o código.
        </p>
      </ConfirmModal>

      <ConfirmModal
        open={leaving}
        title="Sair do grupo?"
        confirmLabel="Sair"
        onConfirm={confirmLeave}
        onClose={() => setLeaving(false)}
      >
        <p>Você deixará de participar deste amigo secreto. Para voltar, use o código de novo.</p>
      </ConfirmModal>
    </div>
  );
}
