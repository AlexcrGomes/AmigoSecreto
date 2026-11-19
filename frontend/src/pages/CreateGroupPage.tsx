import { useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router';
import { createGroup, type EventDetailsBody } from '../api/groups';
import { EventDetailsForm } from '../components/group/EventDetailsForm';
import { buttonClasses } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { GROUPS_KEY, groupKey } from '../hooks/useGroup';

/**
 * /grupos/novo — o criador vira organizador e participante (FR-008, FR-009 da 001) e informa desde já a
 * data, o local e a descrição do evento (FR-001 a FR-003 da 002).
 */
export function CreateGroupPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  async function create(details: EventDetailsBody, name: string) {
    const group = await createGroup(name, details);
    queryClient.setQueryData(groupKey(group.id), group);
    void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
    navigate(`/grupos/${group.id}`, { replace: true });
  }

  return (
    <div className="py-6">
      <Card>
        <h1 className="text-2xl font-bold text-text">Criar grupo</h1>
        <div className="mt-6">
          <EventDetailsForm
            withName
            submitLabel="Criar grupo"
            onSubmit={create}
            footer={
              <Link to="/" className={buttonClasses('ghost', true)}>
                Cancelar
              </Link>
            }
          />
        </div>
      </Card>
    </div>
  );
}
