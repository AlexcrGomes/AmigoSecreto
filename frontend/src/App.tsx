import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { BrowserRouter, Outlet, Route, Routes } from 'react-router';
import { ApiError, apiFetch } from './api/client';
import { AuthProvider } from './auth/AuthProvider';
import { GuestOnly, RequireAuth } from './auth/RequireAuth';
import { AppHeader } from './components/AppHeader';
import { ToastProvider } from './components/ui/Toast';
import { CreateGroupPage } from './pages/CreateGroupPage';
import { GroupPage } from './pages/GroupPage';
import { HomePage } from './pages/HomePage';
import { JoinGroupPage } from './pages/JoinGroupPage';
import { LoginPage } from './pages/LoginPage';
import { PagePlaceholder } from './pages/PagePlaceholder';
import { RegisterPage } from './pages/RegisterPage';

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
      <AppHeader />
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
        <Route element={<GuestOnly />}>
          <Route path="/entrar" element={<LoginPage />} />
          <Route path="/cadastro" element={<RegisterPage />} />
        </Route>
        <Route element={<RequireAuth />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/grupos/novo" element={<CreateGroupPage />} />
          <Route path="/entrar-no-grupo" element={<JoinGroupPage />} />
          <Route path="/grupos/:id" element={<GroupPage />} />
        </Route>
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
      <AuthProvider>
        <ToastProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
