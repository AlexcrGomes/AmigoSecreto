import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import type { ExitRequestNotice } from '../api/exitRequests';
import { server } from '../test/server';
import { useExitRequestNotices } from './useExitRequestNotices';

const REJECTED: ExitRequestNotice = {
  id: 'r-1',
  groupId: 'g-1',
  groupName: 'Natal 2026',
  status: 'REJECTED',
  decidedAt: '2026-12-03T12:00:00Z',
};

function setup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return renderHook(() => useExitRequestNotices(), { wrapper });
}

describe('useExitRequestNotices', () => {
  it('traz os avisos do usuário da sessão', async () => {
    server.use(http.get('/api/v1/exit-request-notices', () => HttpResponse.json([REJECTED])));

    const { result } = setup();

    await waitFor(() => expect(result.current.notices).toEqual([REJECTED]));
  });

  it('sem resposta ainda, a lista é vazia', () => {
    const { result } = setup();

    expect(result.current.notices).toEqual([]);
  });

  it('acknowledge marca como visto e recarrega a lista', async () => {
    let seen = false;
    let acked: string | undefined;
    server.use(
      http.get('/api/v1/exit-request-notices', () => HttpResponse.json(seen ? [] : [REJECTED])),
      http.post('/api/v1/exit-request-notices/:id/ack', ({ params }) => {
        acked = String(params.id);
        seen = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { result } = setup();
    await waitFor(() => expect(result.current.notices).toHaveLength(1));

    await act(() => result.current.acknowledge('r-1'));

    expect(acked).toBe('r-1');
    await waitFor(() => expect(result.current.notices).toEqual([]));
  });
});
