import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import type { User } from '../api/auth';
import { AuthProvider } from '../auth/AuthProvider';
import { ToastProvider } from '../components/ui/Toast';
import { server } from './server';

export const ANA: User = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Ana',
  email: 'ana@x.com',
};

/** Define quem é o usuário da sessão para o GET /auth/me (null = sem sessão → 401). */
export function mockSession(user: User | null) {
  server.use(
    http.get('/api/v1/auth/me', () =>
      user
        ? HttpResponse.json(user)
        : HttpResponse.json(
            { type: '/problems/unauthorized', status: 401, detail: 'Faça login para continuar.' },
            { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
          ),
    ),
  );
}

/** Mostra a rota atual, para os testes conferirem navegações e redirecionamentos. */
export function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
}

export function renderWithProviders(ui: ReactNode, { route = '/' }: { route?: string } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ToastProvider>
          <MemoryRouter initialEntries={[route]}>
            {ui}
            <LocationProbe />
          </MemoryRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>,
  );
}
