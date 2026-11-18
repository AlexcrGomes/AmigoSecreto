import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { applyFieldErrors, errorMessage } from '../lib/errors';
import { safeRedirect, voltarQuery } from '../lib/redirect';
import { loginSchema, type LoginInput } from '../lib/schemas';

/** /entrar — sem "esqueci minha senha" nesta versão (FR-006). */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const voltar = params.get('voltar');
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = handleSubmit(async (data) => {
    setFormError(null);
    try {
      await login(data);
      navigate(safeRedirect(voltar), { replace: true });
    } catch (error) {
      setFormError(errorMessage(error));
      applyFieldErrors(error, setError, ['email', 'password']);
    }
  });

  return (
    <div className="py-8">
      <Card>
        <h1 className="text-2xl font-bold text-text">Entrar</h1>
        <form noValidate onSubmit={onSubmit} className="mt-6 flex flex-col gap-4">
          {formError && (
            <p role="alert" className="rounded-control border border-danger px-3 py-2 text-danger">
              {formError}
            </p>
          )}
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
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password')}
          />
          <Button type="submit" loading={isSubmitting} fullWidth>
            Entrar
          </Button>
        </form>
        <p className="mt-6 text-center text-text-muted">
          Ainda não tem conta?{' '}
          <Link
            to={`/cadastro${voltarQuery(voltar)}`}
            className="inline-flex min-h-11 items-center font-semibold text-primary underline-offset-4 hover:underline"
          >
            Criar conta
          </Link>
        </p>
      </Card>
    </div>
  );
}
