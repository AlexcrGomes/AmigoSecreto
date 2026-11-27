import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { cancelExitRequest, requestExit, type ExitRequestNotice } from '../../api/exitRequests';
import type { GroupDetail, GroupStatus } from '../../api/groups';
import { groupKey } from '../../hooks/useGroup';
import { errorMessage } from '../../lib/errors';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { ConfirmModal } from '../ui/ConfirmModal';
import { useToast } from '../ui/Toast';

/**
 * Participante (não organizador) de um grupo sorteado (FR-025, contracts/ui-routes.md): pede para sair,
 * com a explicação de que o organizador decide e de que o sorteio será refeito, ou cancela o pedido
 * pendente. O sorteio continua valendo enquanto isso (FR-027).
 */
export function ExitRequestPanel({ group }: { group: GroupDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [asking, setAsking] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirmRequest() {
    await requestExit(group.id);
    setAsking(false);
    toast.show('Pedido enviado ao organizador.', 'success');
    await queryClient.invalidateQueries({ queryKey: groupKey(group.id) });
  }

  async function cancel() {
    setCancelling(true);
    setError(null);
    try {
      await cancelExitRequest(group.id);
      toast.show('Pedido de saída cancelado.', 'success');
      await queryClient.invalidateQueries({ queryKey: groupKey(group.id) });
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setCancelling(false);
    }
  }

  if (group.myExitRequest) {
    return (
      <Card>
        <p role="status" className="font-medium text-text">
          Seu pedido de saída está aguardando o organizador.
        </p>
        <p className="mt-1 text-sm text-text-muted">Enquanto isso, o sorteio continua valendo.</p>
        {error && (
          <p role="alert" className="mt-3 text-danger">
            {error}
          </p>
        )}
        <Button
          variant="secondary"
          fullWidth
          className="mt-4"
          loading={cancelling}
          onClick={() => void cancel()}
        >
          Cancelar pedido
        </Button>
      </Card>
    );
  }

  return (
    <>
      <Button variant="ghost" fullWidth onClick={() => setAsking(true)}>
        Preciso sair do grupo
      </Button>
      <ConfirmModal
        open={asking}
        title="Pedir para sair?"
        confirmLabel="Pedir para sair"
        onConfirm={confirmRequest}
        onClose={() => setAsking(false)}
      >
        <p>
          O organizador vai decidir. Se ele aprovar, você sai do grupo e o sorteio será refeito para
          todos.
        </p>
      </ConfirmModal>
    </>
  );
}

/** Texto do aviso de pedido recusado ou cancelado automaticamente (contracts/ui-routes.md). */
export function decisionMessage(notice: ExitRequestNotice, status: GroupStatus): string {
  // Pedido de um dependente do usuário (feature 004): a mensagem fala do dependente
  if (notice.subjectName) {
    const who = notice.subjectName;
    if (notice.status === 'REJECTED') return `O organizador recusou o pedido de saída de ${who}.`;
    if (status === 'FINISHED') {
      return `O pedido de saída de ${who} foi cancelado porque o evento aconteceu e o grupo foi finalizado.`;
    }
    return `O pedido de saída de ${who} foi cancelado porque o sorteio foi desfeito. Agora você pode removê-lo diretamente.`;
  }
  if (notice.status === 'REJECTED') return 'O organizador recusou seu pedido de saída.';
  if (status === 'FINISHED') {
    return 'Seu pedido foi cancelado porque o evento aconteceu e o grupo foi finalizado.';
  }
  return 'Seu pedido foi cancelado porque o sorteio foi desfeito. Agora você pode sair do grupo diretamente.';
}

/** Aviso guardado de uma decisão sobre o pedido de saída, até a pessoa tocar em "Entendi" (FR-026). */
export function ExitRequestDecisionNotice({
  notice,
  status,
  onAcknowledge,
}: {
  notice: ExitRequestNotice;
  status: GroupStatus;
  onAcknowledge: (id: string) => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);

  async function acknowledge() {
    setBusy(true);
    try {
      await onAcknowledge(notice.id);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="border border-primary">
      <p role="status" className="text-text">
        {decisionMessage(notice, status)}
      </p>
      <Button
        variant="secondary"
        fullWidth
        className="mt-3"
        loading={busy}
        onClick={() => void acknowledge()}
      >
        Entendi
      </Button>
    </Card>
  );
}
