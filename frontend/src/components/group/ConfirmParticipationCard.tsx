import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { confirmParticipation, type GroupDetail } from '../../api/groups';
import { GROUPS_KEY, groupKey } from '../../hooks/useGroup';
import { formatEventDate } from '../../lib/eventDate';
import { DETAILS_CHANGED, errorMessage, isProblem } from '../../lib/errors';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { useToast } from '../ui/Toast';

interface ConfirmParticipationCardProps {
  group: GroupDetail;
  /** "Não vou participar": abre a confirmação de sair do grupo. */
  onLeave: () => void;
  /** Aviso vindo do servidor (ex.: os detalhes mudaram e é preciso confirmar de novo). */
  notice?: string | null;
}

/**
 * Cartão em destaque para o participante pendente (FR-009, contracts/ui-routes.md): resume a data e o
 * local e pede a confirmação, sempre na versão dos detalhes que está na tela (FR-035).
 */
export function ConfirmParticipationCard({
  group,
  onLeave,
  notice,
}: ConfirmParticipationCardProps) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setConfirming(true);
    setError(null);
    try {
      const updated = await confirmParticipation(group.id, group.detailsVersion);
      queryClient.setQueryData(groupKey(group.id), updated);
      void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
      toast.show('Participação confirmada!', 'success');
    } catch (failure) {
      if (isProblem(failure, 'details-changed')) {
        setError(DETAILS_CHANGED);
        void queryClient.invalidateQueries({ queryKey: groupKey(group.id) });
      } else {
        setError(errorMessage(failure));
      }
    } finally {
      setConfirming(false);
    }
  }

  const details = group.eventDetails;
  // Os dependentes confirmam junto com o responsável (FR-013 da 004)
  const dependentNames = group.myDependents.map((dependent) => dependent.name);
  const confirmLabel =
    dependentNames.length === 0
      ? 'Confirmar participação'
      : `Confirmar participação (você e ${joinNames(dependentNames)})`;

  return (
    <Card className="border border-primary">
      <h2 className="text-lg font-semibold text-text">Confirme sua participação</h2>
      {details && (
        <p className="mt-1 text-text">
          {formatEventDate(details.eventDate, details.eventTime)} · {details.location}
        </p>
      )}
      <p className="mt-1 text-sm text-text-muted">
        Confira os detalhes do evento. O sorteio só acontece quando todos confirmarem.
      </p>
      {notice && !error && (
        <p role="status" className="mt-3 rounded-control border border-primary px-3 py-2 text-text">
          {notice}
        </p>
      )}
      {error && (
        <p role="alert" className="mt-3 rounded-control border border-danger px-3 py-2 text-danger">
          {error}
        </p>
      )}
      <Button fullWidth className="mt-4" loading={confirming} onClick={() => void confirm()}>
        {confirmLabel}
      </Button>
      <Button variant="ghost" fullWidth className="mt-2" disabled={confirming} onClick={onLeave}>
        Não vou participar
      </Button>
    </Card>
  );
}

/** "Lucas", "Lucas e Bia", "Lucas, Bia e Davi". */
function joinNames(names: string[]): string {
  if (names.length <= 1) return names.join('');
  return `${names.slice(0, -1).join(', ')} e ${names[names.length - 1]}`;
}
