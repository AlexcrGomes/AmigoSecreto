import { zodResolver } from '@hookform/resolvers/zod';
import { useId, useMemo, useState, type ReactNode } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import type { EventDetails, EventDetailsBody } from '../../api/groups';
import { applyFieldErrors, errorMessage } from '../../lib/errors';
import { eventDetailsSchema, type EventDetailsInput } from '../../lib/schemas';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';

const DESCRIPTION_MAX = 500;

interface EventDetailsFormProps {
  /** Na criação do grupo o formulário também pede o nome. */
  withName?: boolean;
  /** Valores salvos (edição). */
  initial?: EventDetails | null;
  submitLabel: string;
  /** Salva; um erro lançado aparece no formulário (FR-024). */
  onSubmit: (details: EventDetailsBody, name: string) => Promise<void>;
  /** Ações abaixo do botão de salvar (ex.: Cancelar). */
  footer?: ReactNode;
}

/**
 * Descrição, data, horário e local do evento (FR-001 a FR-003), em horário de Brasília. Campos nativos de
 * data e hora (research R23); as regras repetem as do servidor só para mostrar o erro na hora.
 */
export function EventDetailsForm({
  withName = false,
  initial,
  submitLabel,
  onSubmit,
  footer,
}: EventDetailsFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const descriptionId = useId();
  const schema = useMemo(
    () =>
      eventDetailsSchema({
        withName,
        original: initial
          ? { eventDate: initial.eventDate, eventTime: initial.eventTime ?? '' }
          : undefined,
      }),
    [withName, initial],
  );
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<EventDetailsInput>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      description: initial?.description ?? '',
      eventDate: initial?.eventDate ?? '',
      eventTime: initial?.eventTime ?? '',
      location: initial?.location ?? '',
    },
  });
  const descriptionLength = useWatch({ control, name: 'description' }).length;

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(
        {
          description: values.description || null,
          eventDate: values.eventDate,
          eventTime: values.eventTime || null,
          location: values.location,
        },
        values.name,
      );
    } catch (error) {
      setFormError(errorMessage(error));
      applyFieldErrors(error, setError, [
        'name',
        'description',
        'eventDate',
        'eventTime',
        'location',
      ]);
    }
  });

  const descriptionError = errors.description?.message;
  const counterId = `${descriptionId}-contador`;
  const descriptionErrorId = `${descriptionId}-erro`;

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4">
      {formError && (
        <p role="alert" className="rounded-control border border-danger px-3 py-2 text-danger">
          {formError}
        </p>
      )}
      {withName && (
        <Input
          label="Nome do grupo"
          placeholder="Ex.: Natal da família"
          autoComplete="off"
          error={errors.name?.message}
          {...register('name')}
        />
      )}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input
          label="Data do evento"
          type="date"
          error={errors.eventDate?.message}
          {...register('eventDate')}
        />
        <Input
          label="Horário (opcional)"
          type="time"
          error={errors.eventTime?.message}
          {...register('eventTime')}
        />
      </div>
      <Input
        label="Local do evento"
        placeholder="Ex.: Casa da Ana"
        autoComplete="off"
        error={errors.location?.message}
        {...register('location')}
      />
      <div className="flex flex-col gap-1.5">
        <label htmlFor={descriptionId} className="text-sm font-medium text-text">
          Descrição (opcional)
        </label>
        <textarea
          id={descriptionId}
          rows={3}
          placeholder="Ex.: valor sugerido, tema, o que levar"
          aria-invalid={descriptionError ? true : undefined}
          aria-describedby={[counterId, descriptionError ? descriptionErrorId : null]
            .filter(Boolean)
            .join(' ')}
          className={[
            'min-h-11 w-full rounded-control border bg-surface px-3 py-2 text-base text-text',
            'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary',
            descriptionError ? 'border-danger' : 'border-border',
          ].join(' ')}
          {...register('description')}
        />
        <p
          id={counterId}
          className={`text-right text-sm ${descriptionLength > DESCRIPTION_MAX ? 'text-danger' : 'text-text-muted'}`}
        >
          {descriptionLength}/{DESCRIPTION_MAX}
        </p>
        {descriptionError && (
          <p id={descriptionErrorId} className="text-sm text-danger">
            {descriptionError}
          </p>
        )}
      </div>
      <Button type="submit" loading={isSubmitting} fullWidth>
        {submitLabel}
      </Button>
      {footer}
    </form>
  );
}
