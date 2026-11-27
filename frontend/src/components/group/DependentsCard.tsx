import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  addDependent,
  cancelDependentExit,
  removeDependent,
  renameDependent,
  requestDependentExit,
} from '../../api/dependents';
import type { DependentsBlockedReason, GroupDetail, MyDependent } from '../../api/groups';
import { groupKey } from '../../hooks/useGroup';
import { errorMessage } from '../../lib/errors';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { ConfirmModal } from '../ui/ConfirmModal';
import { Modal } from '../ui/Modal';
import { useToast } from '../ui/Toast';
import { DependentForm } from './DependentForm';

/** Por que não dá para adicionar (contracts/ui-routes.md da 004); sorteado e finalizado não mostram o botão. */
const BLOCKED_MESSAGES: Partial<Record<NonNullable<DependentsBlockedReason>, string>> = {
  'group-full': 'O grupo está cheio (100 participantes).',
  'dependents-limit': 'Você pode adicionar até 5 dependentes.',
  'dependents-locked':
    'Depois da saída de um dependente seu, não dá para adicionar outros neste grupo.',
};

/** `null` = fechado; `'new'` = adicionando; um dependente = renomeando. */
type Editing = MyDependent | 'new' | null;

/**
 * "Meus dependentes" (US1 da 004): quem não tem e-mail, como uma criança, participa pelo responsável. Antes do
 * sorteio, o responsável adiciona, renomeia e remove; só ele verá quem cada dependente tirar.
 */
export function DependentsCard({ group }: { group: GroupDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [editing, setEditing] = useState<Editing>(null);
  const [removing, setRemoving] = useState<MyDependent | null>(null);
  const [exiting, setExiting] = useState<MyDependent | null>(null);
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [exitError, setExitError] = useState<string | null>(null);
  const open = group.status === 'OPEN';
  const drawn = group.status === 'DRAWN';
  const dependents = group.myDependents;

  // Depois do sorteio, sem dependentes, o cartão não tem o que mostrar
  if (!open && dependents.length === 0) return null;

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: groupKey(group.id) });
  }

  async function save(name: string) {
    if (editing === 'new' || editing === null) {
      const added = await addDependent(group.id, name);
      toast.show(`${added.name} foi adicionado ao grupo.`, 'success');
    } else {
      await renameDependent(group.id, editing.id, name);
      toast.show('Nome atualizado.', 'success');
    }
    setEditing(null);
    await refresh();
  }

  async function confirmRemove() {
    if (!removing) return;
    await removeDependent(group.id, removing.id);
    toast.show(`${removing.name} não participa mais do grupo.`, 'success');
    setRemoving(null);
    await refresh();
  }

  async function confirmExit() {
    if (!exiting) return;
    await requestDependentExit(group.id, exiting.id);
    toast.show('Pedido enviado ao organizador.', 'success');
    setExiting(null);
    await refresh();
  }

  async function cancelExit(dependent: MyDependent) {
    setCancelling(dependent.id);
    setExitError(null);
    try {
      await cancelDependentExit(group.id, dependent.id);
      toast.show('Pedido de saída cancelado.', 'success');
      await refresh();
    } catch (failure) {
      setExitError(errorMessage(failure));
    } finally {
      setCancelling(null);
    }
  }

  const blocked = group.dependentsBlockedReason
    ? BLOCKED_MESSAGES[group.dependentsBlockedReason]
    : undefined;

  return (
    <Card>
      <div className="flex items-start justify-between gap-3">
        <h2 id="meus-dependentes" className="text-lg font-semibold text-text">
          Meus dependentes
        </h2>
        <span className="text-sm text-text-muted">
          {dependents.length} de {group.maxDependents}
        </span>
      </div>
      <p className="mt-1 text-sm text-text-muted">
        Adicione quem não tem e-mail, como uma criança. Só você verá quem ele tirar.
      </p>

      {dependents.length > 0 && (
        <ul aria-labelledby="meus-dependentes" className="mt-4 flex flex-col gap-3">
          {dependents.map((dependent) => (
            <li
              key={dependent.id}
              className="rounded-2xl bg-surface/60 p-3 ring-1 ring-border ring-inset"
            >
              <div className="flex items-center justify-between gap-3">
                <p className="min-w-0 truncate font-medium text-text">{dependent.name}</p>
                {open &&
                  (dependent.confirmed ? (
                    <Badge tone="success">Confirmado</Badge>
                  ) : (
                    <Badge>Pendente</Badge>
                  ))}
              </div>
              {open && (
                <p className="mt-1 text-sm text-text-muted">
                  {dependent.confirmed
                    ? 'Confirmado junto com você'
                    : 'Pendente — confirme a sua participação'}
                </p>
              )}
              {open && (
                <div className="mt-3 grid grid-cols-2 gap-2">
                  <Button
                    variant="secondary"
                    aria-label={`Renomear ${dependent.name}`}
                    onClick={() => setEditing(dependent)}
                  >
                    Renomear
                  </Button>
                  <Button
                    variant="ghost"
                    aria-label={`Remover ${dependent.name}`}
                    onClick={() => setRemoving(dependent)}
                  >
                    Remover
                  </Button>
                </div>
              )}
              {drawn &&
                (dependent.exitRequest ? (
                  <div className="mt-3">
                    <p role="status" className="text-sm text-text">
                      Saída de {dependent.name} pendente: aguardando o organizador.
                    </p>
                    <Button
                      variant="secondary"
                      fullWidth
                      className="mt-2"
                      loading={cancelling === dependent.id}
                      aria-label={`Cancelar o pedido de saída de ${dependent.name}`}
                      onClick={() => void cancelExit(dependent)}
                    >
                      Cancelar pedido
                    </Button>
                  </div>
                ) : (
                  <Button
                    variant="ghost"
                    fullWidth
                    className="mt-3"
                    onClick={() => setExiting(dependent)}
                  >
                    Pedir saída de {dependent.name}
                  </Button>
                ))}
            </li>
          ))}
        </ul>
      )}

      {exitError && (
        <p role="alert" className="mt-3 text-danger">
          {exitError}
        </p>
      )}

      {open &&
        (group.canAddDependents ? (
          <Button fullWidth className="mt-4" onClick={() => setEditing('new')}>
            Adicionar dependente
          </Button>
        ) : (
          blocked && <p className="mt-4 text-sm text-text-muted">{blocked}</p>
        ))}

      <Modal
        open={editing !== null}
        title={editing === 'new' ? 'Novo dependente' : 'Renomear dependente'}
        onClose={() => setEditing(null)}
      >
        {editing !== null && (
          <DependentForm
            initialName={editing === 'new' ? '' : editing.name}
            onSubmit={save}
            onCancel={() => setEditing(null)}
          />
        )}
      </Modal>

      <ConfirmModal
        open={exiting !== null}
        title={`Pedir saída de ${exiting?.name ?? ''}?`}
        confirmLabel="Pedir saída"
        onConfirm={confirmExit}
        onClose={() => setExiting(null)}
      >
        <p>
          O organizador vai decidir. Se ele aprovar, {exiting?.name} sai do grupo, o sorteio será
          refeito para todos e você não poderá adicionar outros dependentes neste grupo.
        </p>
      </ConfirmModal>

      <ConfirmModal
        open={removing !== null}
        title="Remover dependente?"
        confirmLabel="Remover"
        onConfirm={confirmRemove}
        onClose={() => setRemoving(null)}
      >
        <p className="break-words">
          {removing?.name} sairá do grupo, junto com a lista de desejos dele.
        </p>
      </ConfirmModal>
    </Card>
  );
}
