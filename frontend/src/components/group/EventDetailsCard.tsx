import { useId } from 'react';
import type { EventDetails } from '../../api/groups';
import { formatEventDate } from '../../lib/eventDate';
import { formatGiftValue } from '../../lib/giftValue';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';

interface EventDetailsCardProps {
  details: EventDetails;
  /** Só para o organizador, com o grupo aberto ou sorteado. */
  onEdit?: () => void;
}

/**
 * Data, local e descrição do evento. O texto digitado é exibido como texto do React, com as quebras de
 * linha preservadas e nunca como HTML (FR-007).
 */
export function EventDetailsCard({ details, onEdit }: EventDetailsCardProps) {
  const titleId = useId();
  return (
    <Card>
      <section aria-labelledby={titleId}>
        <h2 id={titleId} className="text-lg font-semibold text-text">
          Detalhes do evento
        </h2>
        <dl className="mt-3 flex flex-col gap-2">
          <div>
            <dt className="text-sm text-text-muted">Quando</dt>
            <dd className="font-medium text-text">
              {formatEventDate(details.eventDate, details.eventTime)}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-text-muted">Onde</dt>
            <dd className="font-medium break-words text-text">{details.location}</dd>
          </div>
          {details.giftValue != null && (
            <div>
              <dt className="text-sm text-text-muted">Valor do presente</dt>
              <dd className="font-medium text-text">{formatGiftValue(details.giftValue)}</dd>
            </div>
          )}
          {details.description && (
            <div>
              <dt className="text-sm text-text-muted">Descrição</dt>
              <dd className="break-words whitespace-pre-line text-text">{details.description}</dd>
            </div>
          )}
        </dl>
        {onEdit && (
          <Button variant="secondary" fullWidth className="mt-4" onClick={onEdit}>
            Editar detalhes
          </Button>
        )}
      </section>
    </Card>
  );
}
