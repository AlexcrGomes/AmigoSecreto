import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation, useSearchParams } from 'react-router';
import { Spinner } from '../components/ui/Spinner';
import { safeRedirect } from '../lib/redirect';
import { useAuth } from './AuthProvider';

function Loading() {
  return (
    <div className="flex justify-center py-16 text-primary">
      <Spinner label="Carregando" className="size-8" />
    </div>
  );
}

/** Rotas 🔒: sem sessão, vai para /entrar?voltar=<rota atual> (contracts/ui-routes.md). */
export function RequireAuth({ children }: { children?: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <Loading />;
  if (status === 'anonymous') {
    const target = location.pathname + location.search;
    return <Navigate to={`/entrar?voltar=${encodeURIComponent(target)}`} replace />;
  }
  return children ?? <Outlet />;
}

/** /entrar e /cadastro: quem já está logado vai para o destino pedido ou para /. */
export function GuestOnly({ children }: { children?: ReactNode }) {
  const { status } = useAuth();
  const [params] = useSearchParams();

  if (status === 'loading') return <Loading />;
  if (status === 'authenticated') {
    return <Navigate to={safeRedirect(params.get('voltar'))} replace />;
  }
  return children ?? <Outlet />;
}
