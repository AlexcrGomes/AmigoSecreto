import { useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { acknowledgeExitRequestNotice, listExitRequestNotices } from '../api/exitRequests';
import {
  leaveGroup,
  removeParticipant,
  updateEventDetails,
  type EventDetailsBody,
  type Participant,
} from '../api/groups';
import { useAuth } from '../auth/AuthProvider';
import { ConfirmParticipationCard } from '../components/group/ConfirmParticipationCard';
import { DependentSecretFriendCards } from '../components/group/DependentSecretFriendCard';
import { DependentsCard } from '../components/group/DependentsCard';
import { DrawButton } from '../components/group/DrawButton';
import { EventDetailsCard } from '../components/group/EventDetailsCard';
import { EventDetailsForm } from '../components/group/EventDetailsForm';
import { ExitRequestDecisionNotice, ExitRequestPanel } from '../components/group/ExitRequestPanel';
import { ExitRequestsList } from '../components/group/ExitRequestsList';
import { ParticipantList } from '../components/group/ParticipantList';
import { SecretFriendCard } from '../components/group/SecretFriendCard';
import { WishlistEditor } from '../components/group/WishlistEditor';
import { ShareCode } from '../components/group/ShareCode';
import { Badge } from '../components/ui/Badge';
import { Button, buttonClasses } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { ConfirmModal } from '../components/ui/ConfirmModal';
import { Modal } from '../components/ui/Modal';
import { SegmentedTabs } from '../components/ui/SegmentedTabs';
import { Notice } from '../components/ui/Notice';
import { Spinner } from '../components/ui/Spinner';
import { useToast } from '../components/ui/Toast';
import { GROUPS_KEY, groupKey, useGroup } from '../hooks/useGroup';
import { useExitRequestNotices } from '../hooks/useExitRequestNotices';
import { useGroupEvents } from '../hooks/useGroupEvents';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { WIDE_QUERY } from '../components/AppNavigation';
import { CONFIRMATIONS_RESET, errorMessage } from '../lib/errors';

type GroupTab = 'resumo' | 'pessoas' | 'desejos';

const GROUP_TABS: { value: GroupTab; label: string }[] = [
  { value: 'resumo', label: 'Resumo' },
  { value: 'pessoas', label: 'Pessoas' },
  { value: 'desejos', label: 'Desejos' },
];

/** `?aba=` do endereço; sem valor ou valor desconhecido = Resumo. */
function parseTab(value: string | null): GroupTab {
  return value === 'pessoas' || value === 'desejos' ? value : 'resumo';
}

/**
 * Uma das três partes da tela do grupo (contracts/ui-routes.md). O título é visível no desenho largo
 * e só para leitores de tela no celular, onde a aba já diz qual parte está aberta.
 */
function GroupPart({
  id,
  title,
  hidden,
  children,
}: {
  id: GroupTab;
  title: string;
  hidden: boolean;
  children: ReactNode;
}) {
  return (
    <section
      aria-labelledby={`parte-${id}`}
      hidden={hidden}
      className="flex min-w-0 flex-col gap-4"
    >
      <h2
        id={`parte-${id}`}
        className="sr-only md:not-sr-only md:text-sm md:font-bold md:tracking-wider md:text-accent-gold md:uppercase"
      >
        {title}
      </h2>
      {children}
    </section>
  );
}

const drawnDate = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });

/**
 * /grupos/:id (contracts/ui-routes.md). OPEN: código, lista ao vivo com quem confirmou, "Realizar
 * sorteio" e "Remover" para o organizador, "Confirmar participação" para quem está pendente e "Sair do
 * grupo" para os demais. DRAWN: selo com a data, "Seu amigo secreto" e a
 * lista só com nomes, "Preciso sair do grupo" para o participante e "Pedidos de saída" para o organizador.
 * FINISHED: somente leitura (FR-018, FR-019).
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
  const [justUndone, setJustUndone] = useState(false);
  /** Muda a cada sorteio desfeito: o cartão do amigo secreto recomeça oculto. */
  const [drawGeneration, setDrawGeneration] = useState(0);
  const exitNotices = useExitRequestNotices();
  const wide = useMediaQuery(WIDE_QUERY);
  const [params, setParams] = useSearchParams();
  const tab = parseTab(params.get('aba'));

  /** A aba fica no endereço, sem empilhar histórico: "voltar" sai do grupo (research R57). */
  function chooseTab(next: GroupTab) {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        if (next === 'resumo') updated.delete('aba');
        else updated.set('aba', next);
        return updated;
      },
      { replace: true },
    );
  }

  /** Saiu do grupo (por conta própria ou removido): descarta os dados e volta para a lista. */
  function exitGroup(message: string, kind: 'success' | 'error') {
    if (id) queryClient.removeQueries({ queryKey: groupKey(id) });
    void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
    toast.show(message, kind);
    navigate('/', { replace: true });
  }

  useGroupEvents(id, {
    onDrawn: () => setJustDrawn(true),
    onRevoked: (reason) => {
      if (reason === 'exit-approved') {
        void acknowledgeApproval();
        exitGroup('Seu pedido de saída foi aprovado. Você saiu do grupo.', 'success');
      } else {
        exitGroup('Você foi removido deste grupo', 'error');
      }
    },
    onDrawUndone: () => {
      setJustUndone(true);
      setDrawGeneration((generation) => generation + 1);
    },
    // O organizador mudou data, horário ou local: quem não é organizador precisa confirmar de novo
    onConfirmationsReset: () => {
      if (group.data && !group.data.organizer) setConfirmationsReset(true);
    },
    onFinished: () => setJustFinished(true),
  });

  /** Quem viu a aprovação ao vivo não precisa do aviso na tela inicial (contracts/sse-events.md). */
  async function acknowledgeApproval() {
    try {
      const notices = await listExitRequestNotices();
      const approval = notices.find((n) => n.groupId === id && n.status === 'APPROVED');
      if (approval) await acknowledgeExitRequestNotice(approval.id);
    } catch {
      // Sem problema: o aviso continua guardado e aparece na tela inicial
    }
  }

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
  // Recusado ou cancelado automaticamente; a aprovação aparece na tela inicial (FR-026)
  const decisions = exitNotices.notices.filter(
    (notice) => notice.groupId === detail.id && notice.status !== 'APPROVED',
  );
  const pending = open && detail.myConfirmation === 'PENDING' && !detail.eventDetailsMissing;
  const confirmedGuest = open && !detail.organizer && detail.myConfirmation === 'CONFIRMED';

  // Quem sai leva os dependentes (FR-016 da 004): o aviso lista quem sai junto
  const removingDependents =
    removing && !removing.dependent
      ? detail.participants
          .filter(
            (participant) => participant.dependent && participant.guardianName === removing.name,
          )
          .map((participant) => participant.name)
      : [];

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
    <div className="flex flex-col gap-4 py-6 md:py-2">
      <div className="surface-glass rounded-card px-5 py-4">
        <h1 className="text-2xl font-extrabold tracking-tight break-words text-text">
          {detail.name}
        </h1>
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

      {/* Celular: abas Resumo · Pessoas · Desejos (FR-017, research R57) */}
      {!wide && (
        <div className="sticky top-[calc(env(safe-area-inset-top)+4.75rem)] z-20">
          <SegmentedTabs
            label="Partes do grupo"
            items={GROUP_TABS}
            value={tab}
            onValueChange={(next) => chooseTab(next as GroupTab)}
          />
        </div>
      )}

      {/* As três partes ficam sempre montadas: trocar de aba ou girar o tablet não perde nada */}
      <div
        className={
          wide
            ? 'grid items-start gap-6 md:grid-cols-[minmax(0,1fr)_18rem] lg:grid-cols-[minmax(0,1fr)_22rem]'
            : 'flex flex-col'
        }
      >
        <GroupPart id="resumo" title="Resumo" hidden={!wide && tab !== 'resumo'}>
          {detail.eventDetails ? (
            <EventDetailsCard
              details={detail.eventDetails}
              onEdit={canEditDetails ? () => setEditingDetails(true) : undefined}
            />
          ) : canEditDetails ? (
            <Card highlight>
              <p className="font-semibold text-text">Informe a data e o local do evento</p>
              <p className="mt-1 text-sm text-text-muted">
                Este grupo foi criado antes de existirem os detalhes do evento.
              </p>
              <Button fullWidth className="mt-4" onClick={() => setEditingDetails(true)}>
                Informar detalhes
              </Button>
            </Card>
          ) : (
            <Notice>O organizador ainda vai informar a data e o local do evento.</Notice>
          )}

          {pending && (
            <ConfirmParticipationCard
              group={detail}
              onLeave={() => setLeaving(true)}
              notice={confirmationsReset ? CONFIRMATIONS_RESET : null}
            />
          )}

          {drawn && justDrawn && (
            <Notice tone="success">
              O sorteio foi realizado! Toque em Revelar para ver quem você tirou.
            </Notice>
          )}

          {decisions.map((notice) => (
            <ExitRequestDecisionNotice
              key={notice.id}
              notice={notice}
              status={detail.status}
              onAcknowledge={exitNotices.acknowledge}
            />
          ))}

          {open && (detail.drawUndone || justUndone) && (
            <Notice>
              Uma pessoa saiu do grupo e o sorteio foi desfeito. O organizador fará um novo sorteio.
            </Notice>
          )}

          {finished && justFinished && (
            <Notice tone="success">O evento aconteceu. Este grupo foi finalizado.</Notice>
          )}

          {open ? (
            <>
              <ShareCode code={detail.code} groupName={detail.name} />
              <DrawButton group={detail} />
            </>
          ) : (
            <>
              <SecretFriendCard
                key={drawGeneration}
                groupId={detail.id}
                giftValue={detail.eventDetails?.giftValue ?? null}
              />
              {/* Um cartão por dependente, só para o responsável (US2 da 004) */}
              <DependentSecretFriendCards
                key={`dependentes-${drawGeneration}`}
                groupId={detail.id}
                dependents={detail.myDependents}
                giftValue={detail.eventDetails?.giftValue ?? null}
              />
            </>
          )}

          {drawn && detail.organizer && <ExitRequestsList group={detail} />}

          {/* O pendente já tem "Não vou participar" no cartão de confirmação */}
          {open && !detail.organizer && !pending && (
            <Button variant="danger" fullWidth onClick={() => setLeaving(true)}>
              Sair do grupo
            </Button>
          )}

          {/* Depois do sorteio, só com a aprovação do organizador (FR-025) */}
          {drawn && !detail.organizer && <ExitRequestPanel group={detail} />}
        </GroupPart>

        <div data-testid="group-side" className={wide ? 'flex flex-col gap-6' : 'contents'}>
          <GroupPart id="pessoas" title="Pessoas" hidden={!wide && tab !== 'pessoas'}>
            <ParticipantList
              participants={detail.participants}
              currentUserId={user?.id}
              onRemove={open && detail.organizer ? setRemoving : undefined}
              showConfirmation={open}
            />

            {/* Quem não tem e-mail participa pelo responsável (US1 da 004) */}
            <DependentsCard group={detail} />
          </GroupPart>

          <GroupPart id="desejos" title="Desejos" hidden={!wide && tab !== 'desejos'}>
            {/* A própria lista de desejos, em todos os status (US1 da 003) */}
            <WishlistEditor group={detail} />

            {/* A lista de desejos de cada dependente, montada pelo responsável (US3 da 004) */}
            {detail.myDependents.map((dependent) => (
              <WishlistEditor key={dependent.id} group={detail} dependent={dependent} />
            ))}
          </GroupPart>
        </div>
      </div>

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
          giftValueLocked={!open}
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
        {removing?.dependent ? (
          <p>{removing.name} sairá do grupo.</p>
        ) : (
          <p>
            {removing?.name} sairá do grupo e será avisado. Enquanto não houver sorteio, poderá
            entrar de novo com o código.
          </p>
        )}
        {removingDependents.length > 0 && (
          <p className="mt-2">
            Os dependentes de {removing?.name} ({removingDependents.join(', ')}) também saem do
            grupo.
          </p>
        )}
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
