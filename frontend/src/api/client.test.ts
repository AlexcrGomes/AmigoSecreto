import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { server } from '../test/server';
import { ApiError, apiFetch } from './client';

function clearCookie(name: string) {
  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
}

describe('apiFetch', () => {
  beforeEach(() => clearCookie('XSRF-TOKEN'));
  afterEach(() => vi.restoreAllMocks());

  it('envia credentials same-origin e devolve o JSON da resposta', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    server.use(http.get('/api/v1/groups', () => HttpResponse.json([{ id: '1', name: 'Natal' }])));

    const data = await apiFetch<{ id: string; name: string }[]>('/groups');

    expect(data).toEqual([{ id: '1', name: 'Natal' }]);
    expect(fetchSpy.mock.calls[0]?.[1]?.credentials).toBe('same-origin');
  });

  it('em POST envia o corpo em JSON e o cabeçalho X-XSRF-TOKEN com o valor do cookie', async () => {
    document.cookie = 'XSRF-TOKEN=token%20abc; path=/';
    let xsrf: string | null = null;
    let body: unknown = null;
    let contentType: string | null = null;
    server.use(
      http.post('/api/v1/groups', async ({ request }) => {
        xsrf = request.headers.get('X-XSRF-TOKEN');
        contentType = request.headers.get('Content-Type');
        body = await request.json();
        return HttpResponse.json({ id: '1' }, { status: 201 });
      }),
    );

    await apiFetch('/groups', { method: 'POST', body: { name: 'Natal' } });

    expect(xsrf).toBe('token abc');
    expect(contentType).toBe('application/json');
    expect(body).toEqual({ name: 'Natal' });
  });

  it('em DELETE também envia o X-XSRF-TOKEN e trata 204 como sem conteúdo', async () => {
    document.cookie = 'XSRF-TOKEN=xyz; path=/';
    let xsrf: string | null = null;
    server.use(
      http.delete('/api/v1/groups/1/participants/me', ({ request }) => {
        xsrf = request.headers.get('X-XSRF-TOKEN');
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const result = await apiFetch('/groups/1/participants/me', { method: 'DELETE' });

    expect(result).toBeUndefined();
    expect(xsrf).toBe('xyz');
  });

  it('não envia X-XSRF-TOKEN em GET', async () => {
    document.cookie = 'XSRF-TOKEN=xyz; path=/';
    let xsrf: string | null = 'nao-lido';
    server.use(
      http.get('/api/v1/auth/me', ({ request }) => {
        xsrf = request.headers.get('X-XSRF-TOKEN');
        return HttpResponse.json({});
      }),
    );

    await apiFetch('/auth/me');

    expect(xsrf).toBeNull();
  });

  it('transforma application/problem+json em ApiError com status, type, detail e errors', async () => {
    server.use(
      http.post('/api/v1/auth/register', () =>
        HttpResponse.json(
          {
            type: '/problems/validation-error',
            title: 'Dados inválidos',
            status: 400,
            detail: 'Verifique os campos destacados.',
            errors: [{ field: 'email', message: 'E-mail inválido.' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const error = await apiFetch('/auth/register', { method: 'POST', body: {} }).catch(
      (e: unknown) => e,
    );

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.type).toBe('/problems/validation-error');
    expect(apiError.detail).toBe('Verifique os campos destacados.');
    expect(apiError.message).toBe('Verifique os campos destacados.');
    expect(apiError.errors).toEqual([{ field: 'email', message: 'E-mail inválido.' }]);
    expect(apiError.fieldError('email')).toBe('E-mail inválido.');
    expect(apiError.fieldError('name')).toBeUndefined();
  });

  it('401 dispara o evento auth:unauthorized', async () => {
    const listener = vi.fn();
    window.addEventListener('auth:unauthorized', listener);
    server.use(
      http.get('/api/v1/auth/me', () =>
        HttpResponse.json(
          { type: '/problems/unauthorized', status: 401, detail: 'Faça login para continuar.' },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    await expect(apiFetch('/auth/me')).rejects.toMatchObject({ status: 401 });

    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener('auth:unauthorized', listener);
  });

  it('429 expõe retryAfter a partir do cabeçalho Retry-After', async () => {
    server.use(
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json(
          {
            type: '/problems/rate-limited',
            status: 429,
            detail: 'Muitas tentativas. Tente novamente em alguns minutos.',
          },
          {
            status: 429,
            headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '120' },
          },
        ),
      ),
    );

    const error = (await apiFetch('/auth/login', { method: 'POST', body: {} }).catch(
      (e: unknown) => e,
    )) as ApiError;

    expect(error.status).toBe(429);
    expect(error.retryAfter).toBe(120);
  });

  it('erro sem corpo de problema vira ApiError com mensagem genérica em pt-BR', async () => {
    server.use(
      http.get(
        '/api/v1/groups',
        () => new HttpResponse('<html>Bad Gateway</html>', { status: 502 }),
      ),
    );

    const error = (await apiFetch('/groups').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.detail).toBe('Não foi possível falar com o servidor. Tente novamente.');
    expect(error.retryAfter).toBeUndefined();
  });

  it('falha de rede vira ApiError com status 0', async () => {
    server.use(http.get('/api/v1/groups', () => HttpResponse.error()));

    const error = (await apiFetch('/groups').catch((e: unknown) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(0);
    expect(error.detail).toBe('Sem conexão. Verifique sua internet e tente novamente.');
  });
});
