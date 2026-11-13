import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { ApiError } from './api/client';
import { App, shouldRetry } from './App';
import { server } from './test/server';

function renderAt(path: string) {
  window.history.pushState({}, '', path);
  return render(<App />);
}

describe('App', () => {
  it('pede o token CSRF ao abrir e mostra o layout mobile-first', async () => {
    let csrfCalls = 0;
    server.use(
      http.get('/api/v1/auth/csrf', () => {
        csrfCalls += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAt('/entrar');

    expect(screen.getByRole('banner')).toHaveTextContent('Amigo Secreto');
    expect(screen.getByRole('main').className).toContain('max-w-md');
    expect(screen.getByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument();
    await waitFor(() => expect(csrfCalls).toBe(1));
  });

  it.each([
    ['/cadastro', 'Criar conta'],
    ['/', 'Meus grupos'],
    ['/grupos/novo', 'Criar grupo'],
    ['/entrar-no-grupo', 'Entrar com código'],
    ['/grupos/123', 'Grupo'],
    ['/nao-existe', 'Página não encontrada'],
  ])('a rota %s mostra a tela "%s"', (path, title) => {
    server.use(http.get('/api/v1/auth/csrf', () => new HttpResponse(null, { status: 204 })));

    renderAt(path);

    expect(screen.getByRole('heading', { level: 1, name: title })).toBeInTheDocument();
  });

  it('falha ao pedir o CSRF não quebra a tela', async () => {
    server.use(http.get('/api/v1/auth/csrf', () => HttpResponse.error()));

    renderAt('/entrar');

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeInTheDocument();
  });
});

describe('shouldRetry', () => {
  it('não repete erros 4xx', () => {
    expect(shouldRetry(0, new ApiError(404, { detail: 'x' }))).toBe(false);
    expect(shouldRetry(0, new ApiError(429, { detail: 'x' }))).toBe(false);
  });

  it('repete até 2 vezes erros de rede ou 5xx', () => {
    expect(shouldRetry(0, new ApiError(0, { detail: 'x' }))).toBe(true);
    expect(shouldRetry(1, new ApiError(503, { detail: 'x' }))).toBe(true);
    expect(shouldRetry(2, new Error('x'))).toBe(false);
  });
});
