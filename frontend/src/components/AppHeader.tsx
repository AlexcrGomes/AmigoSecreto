import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { Button } from './ui/Button';

/** Cabeçalho: nome do app e, com sessão, o nome do usuário e o botão "Sair" (FR-005). */
export function AppHeader() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [leaving, setLeaving] = useState(false);

  async function handleLogout() {
    setLeaving(true);
    try {
      await logout();
    } catch {
      // A sessão local já foi limpa; o servidor expira a sessão sozinho
    } finally {
      setLeaving(false);
      navigate('/entrar', { replace: true });
    }
  }

  return (
    <header className="sticky top-0 z-40 px-4 pt-[max(0.75rem,env(safe-area-inset-top))]">
      <div className="surface-glass mx-auto flex min-h-14 w-full max-w-md items-center justify-between gap-3 rounded-control py-1 pr-1.5 pl-5">
        <Link
          to="/"
          className="inline-flex min-h-11 items-center text-lg font-extrabold tracking-tight text-primary"
        >
          Amigo Secreto
        </Link>
        {user && (
          <div className="flex min-w-0 items-center gap-2">
            <span className="truncate text-sm text-text-muted">{user.name}</span>
            <Button variant="ghost" onClick={handleLogout} loading={leaving}>
              Sair
            </Button>
          </div>
        )}
      </div>
    </header>
  );
}
