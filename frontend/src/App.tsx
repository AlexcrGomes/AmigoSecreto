import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { BrowserRouter, Outlet, Route, Routes } from 'react-router';
import { ApiError, apiFetch } from './api/client';
import { ToastProvider } from './components/ui/Toast';
import { PagePlaceholder } from './pages/PagePlaceholder';

/** Erros 4xx são definitivos (não adianta repetir); rede e 5xx tentam até 2 vezes. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
  return failureCount < 2;
}

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry },
      mutations: { retry: false },
    },
  });
}

/** Layout base mobile-first: coluna única de até 28rem, sem rolagem horizontal em 360px. */
function Layout() {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex min-h-14 w-full max-w-md items-center px-4">
          <span className="text-lg font-bold text-primary">Amigo Secreto</span>
        </div>
      </header>
      <main className="mx-auto w-full max-w-md flex-1 px-4">
        <Outlet />
      </main>
    </div>
  );
}

function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/entrar" element={<PagePlaceholder title="Entrar" />} />
        <Route path="/cadastro" element={<PagePlaceholder title="Criar conta" />} />
        <Route path="/" element={<PagePlaceholder title="Meus grupos" />} />
        <Route path="/grupos/novo" element={<PagePlaceholder title="Criar grupo" />} />
        <Route path="/entrar-no-grupo" element={<PagePlaceholder title="Entrar com código" />} />
        <Route path="/grupos/:id" element={<PagePlaceholder title="Grupo" />} />
        <Route path="*" element={<PagePlaceholder title="Página não encontrada" />} />
      </Route>
    </Routes>
  );
}

export function App() {
  const [queryClient] = useState(createQueryClient);

  useEffect(() => {
    // Garante o cookie XSRF-TOKEN antes de qualquer POST (CSRF no padrão SPA)
    apiFetch('/auth/csrf').catch(() => {
      // sem conexão: a próxima ação mostrará o erro ao usuário
    });
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
}
