import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router';
import { createGroup } from '../api/groups';
import { Button, buttonClasses } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { GROUPS_KEY, groupKey } from '../hooks/useGroup';
import { applyFieldErrors, errorMessage } from '../lib/errors';
import { createGroupSchema, type CreateGroupInput } from '../lib/schemas';

/** /grupos/novo — o criador vira organizador e participante (FR-008, FR-009). */
export function CreateGroupPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateGroupInput>({
    resolver: zodResolver(createGroupSchema),
    defaultValues: { name: '' },
  });

  const onSubmit = handleSubmit(async ({ name }) => {
    setFormError(null);
    try {
      const group = await createGroup(name);
      queryClient.setQueryData(groupKey(group.id), group);
      void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
      navigate(`/grupos/${group.id}`, { replace: true });
    } catch (error) {
      setFormError(errorMessage(error));
      applyFieldErrors(error, setError, ['name']);
    }
  });

  return (
    <div className="py-6">
      <Card>
        <h1 className="text-2xl font-bold text-text">Criar grupo</h1>
        <form noValidate onSubmit={onSubmit} className="mt-6 flex flex-col gap-4">
          {formError && (
            <p role="alert" className="rounded-control border border-danger px-3 py-2 text-danger">
              {formError}
            </p>
          )}
          <Input
            label="Nome do grupo"
            placeholder="Ex.: Natal da família"
            autoComplete="off"
            error={errors.name?.message}
            {...register('name')}
          />
          <Button type="submit" loading={isSubmitting} fullWidth>
            Criar grupo
          </Button>
          <Link to="/" className={buttonClasses('ghost', true)}>
            Cancelar
          </Link>
        </form>
      </Card>
    </div>
  );
}
