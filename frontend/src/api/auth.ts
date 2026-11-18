import type { LoginInput, RegisterInput } from '../lib/schemas';
import { apiFetch } from './client';

/** Schema `User` do contrato. */
export interface User {
  id: string;
  name: string;
  email: string;
}

export function getMe(): Promise<User> {
  return apiFetch<User>('/auth/me');
}

export function register(data: RegisterInput): Promise<User> {
  return apiFetch<User>('/auth/register', { method: 'POST', body: data });
}

export function login(data: LoginInput): Promise<User> {
  return apiFetch<User>('/auth/login', { method: 'POST', body: data });
}

export async function logout(): Promise<void> {
  await apiFetch('/auth/logout', { method: 'POST' });
}
