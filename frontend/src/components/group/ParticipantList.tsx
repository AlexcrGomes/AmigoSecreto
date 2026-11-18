import type { Participant } from '../../api/groups';
import { Badge } from '../ui/Badge';
import { Card } from '../ui/Card';

interface ParticipantListProps {
  participants: Participant[];
  currentUserId: string | undefined;
}

export function participantsLabel(count: number): string {
  return count === 1 ? '1 participante' : `${count} participantes`;
}

/** Lista de participantes (só nomes e o selo do organizador — nunca quem tirou quem). */
export function ParticipantList({ participants, currentUserId }: ParticipantListProps) {
  return (
    <Card>
      <h2 className="text-lg font-semibold text-text">{participantsLabel(participants.length)}</h2>
      <ul aria-label="Participantes" className="mt-3 divide-y divide-border">
        {participants.map((participant) => (
          <li
            key={participant.userId}
            className="flex min-h-11 items-center justify-between gap-3 py-2"
          >
            <span className="min-w-0 truncate text-text">
              {participant.name}
              {participant.userId === currentUserId && (
                <span className="text-text-muted"> (você)</span>
              )}
            </span>
            {participant.organizer && <Badge tone="primary">Organizador</Badge>}
          </li>
        ))}
      </ul>
    </Card>
  );
}
