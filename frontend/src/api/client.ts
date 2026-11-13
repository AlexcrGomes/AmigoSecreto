import type { FieldError, Problem } from './problem';

const API_BASE = '/api/v1';
const XSRF_COOKIE = 'XSRF-TOKEN';
const XSRF_HEADER = 'X-XSRF-TOKEN';

const NETWORK_ERROR = 'Sem conexão. Verifique sua internet e tente novamente.';
const SERVER_ERROR = 'Não foi possível falar com o servidor. Tente novamente.';

/** Evento disparado em toda resposta 401 (sessão ausente ou expirada). */
export const UNAUTHORIZED_EVENT = 'auth:unauthorized';

/** Erro de uma chamada à API, com os dados do Problem (RFC 9457). Status 0 = falha de rede. */
export class ApiError extends Error {
  readonly status: number;
  readonly type: string | undefined;
  readonly detail: string;
  readonly errors: FieldError[];
  /** Segundos até poder tentar de novo (cabeçalho Retry-After do 429). */
  readonly retryAfter: number | undefined;

  constructor(status: number, problem: Problem, retryAfter?: number) {
    super(problem.detail);
    this.name = 'ApiError';
    this.status = status;
    this.type = problem.type;
    this.detail = problem.detail;
    this.errors = problem.errors ?? [];
    this.retryAfter = retryAfter;
  }

  /** Mensagem de erro de um campo do formulário, se houver. */
  fieldError(field: string): string | undefined {
    return this.errors.find((error) => error.field === field)?.message;
  }
}

export interface ApiRequest {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
}

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`;
  const entry = document.cookie.split('; ').find((part) => part.startsWith(prefix));
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : undefined;
}

function parseRetryAfter(value: string | null): number | undefined {
  if (!value) return undefined;
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : undefined;
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = parseRetryAfter(response.headers.get('Retry-After'));
  const contentType = response.headers.get('Content-Type') ?? '';
  let problem: Problem = { detail: SERVER_ERROR };
  if (contentType.includes('json')) {
    try {
      const body = (await response.json()) as Partial<Problem>;
      if (typeof body.detail === 'string' && body.detail) {
        problem = { ...body, detail: body.detail };
      }
    } catch {
      // corpo inválido: fica a mensagem genérica
    }
  }
  return new ApiError(response.status, problem, retryAfter);
}

/**
 * Chamada à API na mesma origem: cookie de sessão (HttpOnly) + CSRF no padrão SPA. Em
 * POST/PUT/PATCH/DELETE envia `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`.
 */
export async function apiFetch<T = unknown>(path: string, request: ApiRequest = {}): Promise<T> {
  const method = request.method ?? 'GET';
  const headers = new Headers({ Accept: 'application/json, application/problem+json' });
  if (request.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (method !== 'GET') {
    const token = readCookie(XSRF_COOKIE);
    if (token) headers.set(XSRF_HEADER, token);
  }

  let response: Response;
  try {
    response = await fetch(new URL(API_BASE + path, window.location.origin), {
      method,
      headers,
      body: request.body === undefined ? undefined : JSON.stringify(request.body),
      credentials: 'same-origin',
      signal: request.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, { detail: NETWORK_ERROR });
  }

  if (!response.ok) {
    const error = await toApiError(response);
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
    }
    throw error;
  }

  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  return (await response.json()) as T;
}
