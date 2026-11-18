import { useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { drawGroup, type GroupDetail } from '../../api/groups';
import { groupKey } from '../../hooks/useGroup';
import { errorMessage } from '../../lib/errors';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { useToast } from '../ui/Toast';

const MIN_PARTICIPANTS = 3;

/** "Realizar sorteio" (só para o organizador, com o grupo OPEN), com confirmação (FR-020, FR-021). */
export function DrawButton({ group }: { group: GroupDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const hintId = useId();
  const [open, setOpen] = useState(false);
  const [drawing, setDrawing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!group.organizer || group.status !== 'OPEN') return null;

  const tooFew = group.participantCount < MIN_PARTICIPANTS;

  async function confirm() {
    setDrawing(true);
    setError(null);
    try {
      await drawGroup(group.id);
      await queryClient.invalidateQueries({ queryKey: groupKey(group.id) });
      setOpen(false);
      toast.show('Sorteio realizado!', 'success');
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setDrawing(false);
    }
  }

  return (
    <div>
      <Button
        fullWidth
        disabled={!group.canDraw}
        aria-describedby={tooFew ? hintId : undefined}
        onClick={() => {
          setError(null);
          setOpen(true);
        }}
      >
        Realizar sorteio
      </Button>
      {tooFew && (
        <p id={hintId} className="mt-2 text-center text-sm text-text-muted">
          São necessários pelo menos 3 participantes
        </p>
      )}
      <Modal
        open={open}
        title="Realizar o sorteio?"
        onClose={() => {
          if (!drawing) setOpen(false);
        }}
        footer={
          <>
            <Button variant="secondary" disabled={drawing} onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button loading={drawing} onClick={() => void confirm()}>
              Sortear
            </Button>
          </>
        }
      >
        <p>
          Depois do sorteio, ninguém mais poderá entrar ou sair do grupo, e o resultado não pode ser
          refeito. Cada participante verá só quem ele mesmo tirou.
        </p>
        {error && (
          <p
            role="alert"
            className="mt-3 rounded-control border border-danger px-3 py-2 text-danger"
          >
            {error}
          </p>
        )}
      </Modal>
    </div>
  );
}
