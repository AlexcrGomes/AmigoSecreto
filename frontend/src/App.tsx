import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { BrowserRouter, Outlet, Route, Routes } from 'react-router';
import { ApiError, apiFetch } from './api/client';
import { AuthProvider, useAuth } from './auth/AuthProvider';
import { AppNavigation, WIDE_QUERY } from './components/AppNavigation';
import { useMediaQuery } from './hooks/useMediaQuery';
import { GuestOnly, RequireAuth } from './auth/RequireAuth';
import { AppBackground } from './components/AppBackground';
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

/**
 * Moldura em dois desenhos (research R56): no celular, cabeçalho compacto, coluna única de até 28rem e
 * barra de abas embaixo; no tablet, iPad e computador, menu lateral e conteúdo até 72rem. A ordem
 * dos filhos não muda entre os desenhos, então girar o tablet não desmonta a tela (US4-7).
 */
function Layout() {
  const { status } = useAuth();
  const wide = useMediaQuery(WIDE_QUERY);
  const loggedIn = status === 'authenticated';

  const mainClasses = wide
    ? loggedIn
      ? 'mx-auto w-full max-w-6xl min-w-0 flex-1 px-6 py-6 lg:px-10 lg:py-8'
      : 'w-full flex-1'
    : [
        'mx-auto w-full max-w-md flex-1 px-4',
        loggedIn ? 'pb-[calc(var(--tabbar-height)+env(safe-area-inset-bottom)+1.5rem)]' : 'pb-8',
      ].join(' ');

  return (
    <div className={wide && loggedIn ? 'flex min-h-dvh' : 'flex min-h-dvh flex-col'}>
      <AppBackground />
      {!wide && <AppHeader />}
      {loggedIn && <AppNavigation />}
      <main className={mainClasses}>
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
