import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthProvider';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { applyFieldErrors, errorMessage } from '../lib/errors';
import { safeRedirect, voltarQuery } from '../lib/redirect';
import { registerSchema, type RegisterInput } from '../lib/schemas';

/** /cadastro — cria a conta e já entra (FR-001, FR-002). */
export function RegisterPage() {
  const { register: createAccount } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const voltar = params.get('voltar');
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterInput>({
    resolver: zodResolver(registerSchema),
    defaultValues: { name: '', email: '', password: '' },
  });

  const onSubmit = handleSubmit(async (data) => {
    setFormError(null);
    try {
      await createAccount(data);
      navigate(safeRedirect(voltar), { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setError('email', { type: 'server', message: error.detail });
        return;
      }
      setFormError(errorMessage(error));
      applyFieldErrors(error, setError, ['name', 'email', 'password']);
    }
  });

  return (
    <div className="py-8">
      <Card>
        <h1 className="text-2xl font-bold text-text">Criar conta</h1>
        <form noValidate onSubmit={onSubmit} className="mt-6 flex flex-col gap-4">
          {formError && (
            <p role="alert" className="rounded-control border border-danger px-3 py-2 text-danger">
              {formError}
            </p>
          )}
          <Input
            label="Nome"
            autoComplete="name"
            error={errors.name?.message}
            {...register('name')}
          />
          <Input
            label="E-mail"
            type="email"
            inputMode="email"
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
            error={errors.email?.message}
            {...register('email')}
          />
          <Input
            label="Senha"
            type="password"
            autoComplete="new-password"
            hint="De 8 a 64 caracteres."
            error={errors.password?.message}
            {...register('password')}
          />
          <Button type="submit" loading={isSubmitting} fullWidth>
            Criar conta
          </Button>
        </form>
        <p className="mt-6 text-center text-text-muted">
          Já tem conta?{' '}
          <Link
            to={`/entrar${voltarQuery(voltar)}`}
            className="inline-flex min-h-11 items-center font-semibold text-primary underline-offset-4 hover:underline"
          >
            Entrar
          </Link>
        </p>
      </Card>
    </div>
  );
}
