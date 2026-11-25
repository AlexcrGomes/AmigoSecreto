import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';

interface ExternalLinkModalProps {
  /** Endereço a abrir; `null` = fechado. */
  url: string | null;
  onClose: () => void;
}

/**
 * Aviso antes de abrir o link de um item da lista de desejos (FR-017, research R29): mostra o endereço e abre
 * em outra aba com `noopener,noreferrer` — o site não recebe a página de origem nem controla esta aba.
 */
export function ExternalLinkModal({ url, onClose }: ExternalLinkModalProps) {
  function open() {
    if (url) window.open(url, '_blank', 'noopener,noreferrer');
    onClose();
  }

  return (
    <Modal
      open={url !== null}
      title="Abrir site externo?"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={open}>Abrir</Button>
        </>
      }
    >
      <p>Você vai abrir um site externo:</p>
      <p className="mt-2 font-medium break-all text-text">{url}</p>
      <p className="mt-2 text-sm text-text-muted">O Amigo Secreto não controla esse site.</p>
    </Modal>
  );
}
