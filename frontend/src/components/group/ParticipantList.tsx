import type { Participant } from '../../api/groups';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';

interface ParticipantListProps {
  participants: Participant[];
  currentUserId: string | undefined;
  /** Só para o organizador, com o grupo OPEN: mostra "Remover" nos demais participantes. */
  onRemove?: (participant: Participant) => void;
}

export function participantsLabel(count: number): string {
  return count === 1 ? '1 participante' : `${count} participantes`;
}

/** Lista de participantes (só nomes e o selo do organizador — nunca quem tirou quem). */
export function ParticipantList({ participants, currentUserId, onRemove }: ParticipantListProps) {
  return (
    <Card>
      <h2 className="text-lg font-semibold text-text">{participantsLabel(participants.length)}</h2>
      <ul aria-label="Participantes" className="mt-3 divide-y divide-border">
        {participants.map((participant) => {
          const isMe = participant.userId === currentUserId;
          const removable = onRemove && !isMe && !participant.organizer;
          return (
            <li
              key={participant.userId}
              className="flex min-h-11 items-center justify-between gap-3 py-2"
            >
              <span className="min-w-0 truncate text-text">
                {participant.name}
                {isMe && <span className="text-text-muted"> (você)</span>}
              </span>
              <span className="flex shrink-0 items-center gap-2">
                {participant.organizer && <Badge tone="primary">Organizador</Badge>}
                {removable && (
                  <Button
                    variant="ghost"
                    aria-label={`Remover ${participant.name}`}
                    onClick={() => onRemove(participant)}
                  >
                    Remover
                  </Button>
                )}
              </span>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}
