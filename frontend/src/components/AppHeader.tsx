import { Link } from 'react-router';
import { Brand } from './Brand';

/**
 * Cabeçalho compacto do celular (research R56): só a marca, que leva para "Meus grupos". A navegação
 * e o "Sair" ficam na AppNavigation (barra embaixo no celular, menu lateral no desenho largo).
 */
export function AppHeader() {
  return (
    <header className="sticky top-0 z-30 px-4 pt-[max(0.75rem,env(safe-area-inset-top))]">
      <div className="surface-glass mx-auto flex min-h-14 w-full max-w-md items-center rounded-control px-3">
        <Link to="/" aria-label="Amigo Secreto" className="inline-flex min-h-11 items-center">
          <Brand />
        </Link>
      </div>
    </header>
  );
}
