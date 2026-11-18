import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createContext, useContext, useEffect, useMemo, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import type { User } from '../api/auth';
import { ApiError, UNAUTHORIZED_EVENT } from '../api/client';
import type { LoginInput, RegisterInput } from '../lib/schemas';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  user: User | null;
  status: AuthStatus;
  login: (data: LoginInput) => Promise<User>;
  register: (data: RegisterInput) => Promise<User>;
  logout: () => Promise<void>;
}

const ME_KEY = ['auth', 'me'] as const;

const AuthContext = createContext<AuthContextValue | null>(null);

/** Usuário da sessão: null quando o servidor responde 401. */
async function fetchMeOrNull(): Promise<User | null> {
  try {
    return await authApi.getMe();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}

/**
 * Estado de autenticação do app. A sessão vive num cookie HttpOnly no servidor; aqui só guardamos o
 * usuário retornado por `GET /auth/me`. Qualquer 401 da API (`auth:unauthorized`) limpa o usuário.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ME_KEY, queryFn: fetchMeOrNull, staleTime: Infinity });

  useEffect(() => {
    const onUnauthorized = () => queryClient.setQueryData(ME_KEY, null);
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(() => {
    const user = me.data ?? null;
    const status: AuthStatus = me.isPending ? 'loading' : user ? 'authenticated' : 'anonymous';
    return {
      user,
      status,
      login: async (data) => {
        const logged = await authApi.login(data);
        queryClient.setQueryData(ME_KEY, logged);
        return logged;
      },
      register: async (data) => {
        const created = await authApi.register(data);
        queryClient.setQueryData(ME_KEY, created);
        return created;
      },
      logout: async () => {
        try {
          await authApi.logout();
        } finally {
          // Nenhum dado do usuário anterior fica em cache (grupos, amigo secreto…). A consulta do
          // usuário fica, mas zerada: removê-la faria o observador ativo buscá-la de novo.
          queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== ME_KEY[0] });
          queryClient.setQueryData(ME_KEY, null);
        }
      },
    };
  }, [me.data, me.isPending, queryClient]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth precisa estar dentro de <AuthProvider>');
  }
  return context;
}
