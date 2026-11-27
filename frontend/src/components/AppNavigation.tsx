import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { Brand } from './Brand';
import { BottomTabBar } from './ui/BottomTabBar';
import { ConfirmModal } from './ui/ConfirmModal';
import { IconGroups, IconLogout, IconPlus, IconTicket } from './ui/icons';

export const WIDE_QUERY = '(min-width: 768px)';

type Destination = 'grupos' | 'criar' | 'entrar';

const DESTINATIONS: {
  value: Destination;
  to: string;
  label: string;
  short: string;
  Icon: typeof IconGroups;
}[] = [
  { value: 'grupos', to: '/', label: 'Meus grupos', short: 'Grupos', Icon: IconGroups },
  { value: 'criar', to: '/grupos/novo', label: 'Criar grupo', short: 'Criar', Icon: IconPlus },
  {
    value: 'entrar',
    to: '/entrar-no-grupo',
    label: 'Entrar com código',
    short: 'Entrar',
    Icon: IconTicket,
  },
];

/** Destino marcado pela rota atual (contracts/ui-routes.md). */
export function currentDestination(pathname: string): Destination | null {
  if (pathname === '/grupos/novo') return 'criar';
  if (pathname === '/entrar-no-grupo') return 'entrar';
  if (pathname === '/' || pathname.startsWith('/grupos/')) return 'grupos';
  return null;
}

/** Campo que abre o teclado do celular. */
function opensKeyboard(target: EventTarget | null): boolean {
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return true;
  if (!(target instanceof HTMLInputElement)) return false;
  return !['button', 'submit', 'checkbox', 'radio', 'reset', 'file'].includes(target.type);
}

/** Esconde a barra de baixo enquanto um campo está em foco, para não cobrir o campo (R49). */
function useKeyboardOpen(enabled: boolean): boolean {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (!enabled) return;
    const onFocusIn = (event: FocusEvent) => setOpen(opensKeyboard(event.target));
    const onFocusOut = () => setOpen(false);
    document.addEventListener('focusin', onFocusIn);
    document.addEventListener('focusout', onFocusOut);
    return () => {
      document.removeEventListener('focusin', onFocusIn);
      document.removeEventListener('focusout', onFocusOut);
    };
  }, [enabled]);
  return enabled && open;
}

/**
 * Navegação das telas logadas (FR-015, research R49 e R58): no celular, a barra de abas da Glisse
 * fixa embaixo; no tablet, iPad e computador, um menu lateral de vidro. Uma só no DOM por vez.
 * "Sair" sempre pede confirmação.
 */
export function AppNavigation() {
  const wide = useMediaQuery(WIDE_QUERY);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [confirming, setConfirming] = useState(false);
  const keyboardOpen = useKeyboardOpen(!wide);
  const current = currentDestination(pathname);

  async function leave() {
    try {
      await logout();
    } catch {
      // A sessão local já foi limpa; o servidor expira a sessão sozinho
    }
    setConfirming(false);
    navigate('/entrar', { replace: true });
  }

  return (
    <>
      {wide ? (
        <SideNav current={current} userName={user?.name} onLogout={() => setConfirming(true)} />
      ) : (
        !keyboardOpen && (
          <div
            data-tabbar=""
            className="fixed inset-x-0 bottom-0 z-40 flex justify-center px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]"
          >
            <BottomTabBar
              label="Menu principal"
              value={current}
              onAction={() => setConfirming(true)}
              items={[
                // Rótulo curto na tela (72px por aba); o nome acessível é o completo
                ...DESTINATIONS.map(({ value, to, short, label, Icon }) => ({
                  value,
                  to,
                  label: (
                    <>
                      <span aria-hidden="true">{short}</span>
                      <span className="sr-only">{label}</span>
                    </>
                  ),
                  icon: <Icon />,
                })),
                { value: 'sair', label: 'Sair', icon: <IconLogout /> },
              ]}
            />
          </div>
        )
      )}
      <ConfirmModal
        open={confirming}
        title="Sair da sua conta?"
        confirmLabel="Sair"
        onConfirm={leave}
        onClose={() => setConfirming(false)}
      >
        <p>Para voltar, é só entrar de novo com seu e-mail e senha.</p>
      </ConfirmModal>
    </>
  );
}

function SideNav({
  current,
  userName,
  onLogout,
}: {
  current: Destination | null;
  userName: string | undefined;
  onLogout: () => void;
}) {
  return (
    <div className="sticky top-0 h-dvh shrink-0 p-3 md:w-24 lg:w-72">
      <nav
        aria-label="Menu principal"
        className="surface-glass flex h-full flex-col rounded-card px-2 py-5 lg:px-4"
      >
        <Link
          to="/"
          className="flex min-h-11 items-center justify-center rounded-control lg:justify-start lg:px-2"
        >
          <span className="lg:hidden">
            <Brand compact />
          </span>
          <span className="hidden lg:inline-flex">
            <Brand />
          </span>
        </Link>
        <span aria-hidden="true" className="mx-auto mt-5 mb-4 h-px w-10 bg-gold-soft lg:w-full" />
        <ul className="flex flex-col gap-1.5">
          {DESTINATIONS.map(({ value, to, label, short, Icon }) => {
            const active = current === value;
            return (
              <li key={value}>
                <Link
                  to={to}
                  aria-label={label}
                  aria-current={active ? 'page' : undefined}
                  className={[
                    'flex min-h-11 flex-col items-center justify-center gap-1 rounded-2xl px-2 py-2 text-xs font-semibold transition-colors',
                    'lg:flex-row lg:justify-start lg:gap-3 lg:px-4 lg:text-base',
                    active
                      ? 'bg-primary-fill text-on-primary shadow-sm'
                      : 'text-text hover:bg-surface/70',
                  ].join(' ')}
                >
                  <Icon className={`size-6 ${active ? '' : 'text-accent-gold'}`} />
                  <span aria-hidden="true" className="lg:hidden">
                    {short}
                  </span>
                  <span aria-hidden="true" className="hidden lg:inline">
                    {label}
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
        <div className="mt-auto flex flex-col items-center gap-2 border-t border-border pt-4 lg:items-stretch">
          {userName && (
            <p className="flex min-w-0 items-center gap-2 lg:px-2">
              <span
                aria-hidden="true"
                className="grid size-9 shrink-0 place-items-center rounded-full bg-gold-soft/40 font-bold text-accent-gold ring-1 ring-gold-soft"
              >
                {userName.charAt(0).toUpperCase()}
              </span>
              <span className="sr-only lg:not-sr-only lg:truncate lg:font-semibold lg:text-text">
                {userName}
              </span>
            </p>
          )}
          <button
            type="button"
            onClick={onLogout}
            aria-label="Sair"
            className="flex min-h-11 w-full flex-col items-center justify-center gap-1 rounded-2xl px-2 py-2 text-xs font-semibold text-primary hover:bg-surface/70 lg:flex-row lg:justify-start lg:gap-3 lg:px-4 lg:text-base"
          >
            <IconLogout className="size-6" />
            <span aria-hidden="true">Sair</span>
          </button>
        </div>
      </nav>
    </div>
  );
}
