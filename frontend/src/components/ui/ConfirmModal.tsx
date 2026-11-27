import { useState, type ReactNode } from 'react';
import { errorMessage } from '../../lib/errors';
import { Button, type ButtonVariant } from './Button';
import { IconAlert } from './icons';
import { Modal } from './Modal';

interface ConfirmModalProps {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  confirmVariant?: ButtonVariant;
  /** Ação confirmada; se falhar, o erro aparece no próprio diálogo. */
  onConfirm: () => Promise<void>;
  onClose: () => void;
}

/** Confirmação de uma ação com consequência (sair, remover…), com loading e erro dentro do diálogo. */
export function ConfirmModal({
  open,
  title,
  children,
  confirmLabel,
  confirmVariant = 'danger',
  onConfirm,
  onClose,
}: ConfirmModalProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function close() {
    if (busy) return;
    setError(null);
    onClose();
  }

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      title={title}
      onClose={close}
      footer={
        <>
          <Button variant="secondary" disabled={busy} onClick={close}>
            Cancelar
          </Button>
          <Button variant={confirmVariant} loading={busy} onClick={() => void confirm()}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {children}
      {error && (
        <p
          role="alert"
          className="mt-3 flex items-start gap-2 rounded-field border border-danger bg-surface px-3 py-2 font-medium text-danger"
        >
          <IconAlert className="mt-0.5 size-4 shrink-0" />
          {error}
        </p>
      )}
    </Modal>
  );
}
