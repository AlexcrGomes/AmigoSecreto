import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

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

const KIND_STYLES: Record<ToastKind, string> = {
  info: 'border-border',
  success: 'border-success',
  error: 'border-danger',
};

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
        className="pointer-events-none fixed inset-x-0 bottom-4 z-50 mx-auto flex w-full max-w-md flex-col gap-2 px-4"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`pointer-events-auto flex items-center justify-between gap-3 rounded-control border-l-4 bg-surface py-2 pr-1 pl-4 text-text shadow-card ${KIND_STYLES[toast.kind]}`}
          >
            <span>{toast.message}</span>
            <button
              type="button"
              aria-label="Fechar aviso"
              onClick={() => dismiss(toast.id)}
              className="min-h-11 min-w-11 rounded-control text-text-muted hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary"
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
