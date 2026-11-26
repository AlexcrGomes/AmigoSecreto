import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { applyFieldErrors, errorMessage } from '../../lib/errors';
import { dependentSchema, type DependentInput } from '../../lib/schemas';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';

interface DependentFormProps {
  /** Nome atual, ao renomear; vazio ao adicionar. */
  initialName?: string;
  /** Salva; um erro lançado aparece no formulário. */
  onSubmit: (name: string) => Promise<void>;
  onCancel: () => void;
}

/** Nome do dependente (2 a 80 caracteres, FR-001 da 004). Texto sempre como texto. */
export function DependentForm({ initialName = '', onSubmit, onCancel }: DependentFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DependentInput>({
    resolver: zodResolver(dependentSchema),
    defaultValues: { name: initialName },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values.name);
    } catch (error) {
      applyFieldErrors(error, setError, ['name']);
      setFormError(errorMessage(error));
    }
  });

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4">
      {formError && (
        <p role="alert" className="text-danger">
          {formError}
        </p>
      )}
      <Input
        label="Nome"
        placeholder="Ex.: Lucas"
        autoComplete="off"
        maxLength={80}
        error={errors.name?.message}
        {...register('name')}
      />
      <Button type="submit" fullWidth loading={isSubmitting}>
        Salvar
      </Button>
      <Button variant="ghost" fullWidth disabled={isSubmitting} onClick={onCancel}>
        Cancelar
      </Button>
    </form>
  );
}
