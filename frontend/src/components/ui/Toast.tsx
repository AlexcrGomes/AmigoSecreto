import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { IconAlert, IconCheck } from './icons';

type ToastKind = 'info' | 'success' | 'error';

interface ToastItem {
  id: number;
  message: string;
  kind: ToastKind;
}

interface ToastApi {
  show: (message: string, kind?: ToastKind) => void;
}

const DURATION_MS = 4000;

const ToastContext = createContext<ToastApi | null>(null);

/** Ícone de cada tipo de aviso: sucesso e erro não dependem só da cor (FR-009). */
function KindIcon({ kind }: { kind: ToastKind }) {
  if (kind === 'success') {
    return <IconCheck data-testid="toast-icon-success" className="size-5 shrink-0 text-success" />;
  }
  if (kind === 'error') {
    return <IconAlert data-testid="toast-icon-error" className="size-5 shrink-0 text-danger" />;
  }
  return null;
}

let nextId = 1;

/** Avisos curtos anunciados por leitores de tela (aria-live). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const show = useCallback(
    (message: string, kind: ToastKind = 'info') => {
      const id = nextId++;
      setToasts((current) => [...current, { id, message, kind }]);
      window.setTimeout(() => dismiss(id), DURATION_MS);
    },
    [dismiss],
  );

  const api = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-[calc(var(--toast-offset,1rem)+env(safe-area-inset-bottom))] z-50 mx-auto flex w-full max-w-md flex-col gap-2 px-4"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className="surface-glass pointer-events-auto flex items-center gap-3 rounded-3xl py-1.5 pr-1 pl-4 text-text"
          >
            <KindIcon kind={toast.kind} />
            <span className="flex-1 font-medium">{toast.message}</span>
            <button
              type="button"
              aria-label="Fechar aviso"
              onClick={() => dismiss(toast.id)}
              className="min-h-11 min-w-11 rounded-control text-text-muted hover:bg-surface/60"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast precisa estar dentro de <ToastProvider>');
  }
  return context;
}
