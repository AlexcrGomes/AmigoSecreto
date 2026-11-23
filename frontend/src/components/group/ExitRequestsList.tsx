import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { approveExitRequest, rejectExitRequest } from '../../api/exitRequests';
import type { ExitRequest, GroupDetail } from '../../api/groups';
import { GROUPS_KEY, groupKey } from '../../hooks/useGroup';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { ConfirmModal } from '../ui/ConfirmModal';
import { useToast } from '../ui/Toast';

const requestedDate = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });

type Decision = { request: ExitRequest; kind: 'approve' | 'reject' };

/**
 * "Pedidos de saída" do organizador (FR-026, SC-009): nome e data de cada pedido, com Aprovar e Recusar,
 * cada um com confirmação. Nunca mostra quem a pessoa havia tirado (FR-032).
 */
export function ExitRequestsList({ group }: { group: GroupDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [deciding, setDeciding] = useState<Decision | null>(null);

  async function confirmDecision() {
    if (!deciding) return;
    const { request, kind } = deciding;
    const updated =
      kind === 'approve'
        ? await approveExitRequest(group.id, request.id)
        : await rejectExitRequest(group.id, request.id);
    queryClient.setQueryData(groupKey(group.id), updated);
    void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
    setDeciding(null);
    toast.show(
      kind === 'approve'
        ? `${request.name} saiu do grupo. Faça um novo sorteio.`
        : 'Pedido de saída recusado.',
      'success',
    );
  }

  if (group.pendingExitRequests.length === 0) return null;

  return (
    <Card>
      <h2 id="pedidos-de-saida" className="text-lg font-semibold text-text">
        Pedidos de saída
      </h2>
      <ul aria-labelledby="pedidos-de-saida" className="mt-3 flex flex-col gap-3">
        {group.pendingExitRequests.map((request) => (
          <li key={request.id} className="rounded-control border border-border p-3">
            <p className="font-medium break-words text-text">{request.name}</p>
            <p className="text-sm text-text-muted">
              Pediu para sair em {requestedDate.format(new Date(request.requestedAt))}
            </p>
            <div className="mt-3 grid grid-cols-2 gap-2">
              <Button onClick={() => setDeciding({ request, kind: 'approve' })}>Aprovar</Button>
              <Button variant="secondary" onClick={() => setDeciding({ request, kind: 'reject' })}>
                Recusar
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <ConfirmModal
        open={deciding?.kind === 'approve'}
        title="Aprovar a saída?"
        confirmLabel="Aprovar"
        onConfirm={confirmDecision}
        onClose={() => setDeciding(null)}
      >
        <p>
          {deciding?.request.name} sairá do grupo, o sorteio será desfeito para todos e você
          precisará sortear de novo.
        </p>
      </ConfirmModal>

      <ConfirmModal
        open={deciding?.kind === 'reject'}
        title="Recusar o pedido?"
        confirmLabel="Recusar"
        confirmVariant="primary"
        onConfirm={confirmDecision}
        onClose={() => setDeciding(null)}
      >
        <p>{deciding?.request.name} continua no grupo, com o mesmo amigo secreto.</p>
      </ConfirmModal>
    </Card>
  );
}
