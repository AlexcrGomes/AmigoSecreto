import { useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { joinGroup } from '../api/groups';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { GROUPS_KEY, groupKey } from '../hooks/useGroup';
import { errorMessage } from '../lib/errors';
import { maskCode, normalizeCode } from '../lib/groupCode';

const INVALID_CODE = 'Código inválido.';

function joinErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 404) return INVALID_CODE;
    if (error.type === '/problems/group-already-drawn') {
      return 'O sorteio deste grupo já foi realizado.';
    }
    if (error.type === '/problems/group-full') return 'Este grupo está cheio.';
  }
  return errorMessage(error);
}

/** /entrar-no-grupo — aceita `?codigo=` vindo de um link compartilhado (FR-011, FR-013). */
export function JoinGroupPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const [code, setCode] = useState(() => maskCode(params.get('codigo') ?? ''));
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!normalizeCode(code)) {
      setError(INVALID_CODE);
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const group = await joinGroup(code);
      queryClient.setQueryData(groupKey(group.id), group);
      void queryClient.invalidateQueries({ queryKey: GROUPS_KEY });
      navigate(`/grupos/${group.id}`, { replace: true });
    } catch (failure) {
      setError(joinErrorMessage(failure));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="py-6">
      <Card>
        <h1 className="text-2xl font-bold text-text">Entrar com código</h1>
        <p className="mt-2 text-text-muted">Digite o código que o organizador compartilhou.</p>
        <form
          noValidate
          onSubmit={(event) => void onSubmit(event)}
          className="mt-6 flex flex-col gap-4"
        >
          <Input
            label="Código do grupo"
            placeholder="XXXX-XXXX"
            value={code}
            onChange={(event) => setCode(maskCode(event.target.value))}
            autoCapitalize="characters"
            autoCorrect="off"
            autoComplete="off"
            spellCheck={false}
            className="font-mono text-lg tracking-widest uppercase"
            error={error ?? undefined}
          />
          <Button type="submit" loading={submitting} fullWidth>
            Entrar no grupo
          </Button>
        </form>
      </Card>
    </div>
  );
}
