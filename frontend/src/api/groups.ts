import { apiFetch } from './client';

/** Tipos dos schemas de contracts/openapi.yaml. */
export type GroupStatus = 'OPEN' | 'DRAWN';

export interface GroupSummary {
  id: string;
  name: string;
  status: GroupStatus;
  participantCount: number;
  organizer: boolean;
}

export interface Participant {
  userId: string;
  name: string;
  organizer: boolean;
}

export interface GroupDetail {
  id: string;
  name: string;
  /** Formatado `XXXX-XXXX`. */
  code: string;
  status: GroupStatus;
  drawnAt: string | null;
  participants: Participant[];
  participantCount: number;
  maxParticipants: number;
  organizer: boolean;
  canDraw: boolean;
}

export function listGroups(signal?: AbortSignal): Promise<GroupSummary[]> {
  return apiFetch<GroupSummary[]>('/groups', { signal });
}

export function createGroup(name: string): Promise<GroupDetail> {
  return apiFetch<GroupDetail>('/groups', { method: 'POST', body: { name } });
}

export function joinGroup(code: string): Promise<GroupDetail> {
  return apiFetch<GroupDetail>('/groups/join', { method: 'POST', body: { code } });
}

export function getGroup(id: string, signal?: AbortSignal): Promise<GroupDetail> {
  return apiFetch<GroupDetail>(`/groups/${encodeURIComponent(id)}`, { signal });
}

/** Schema `DrawResult`: confirmação do sorteio, sem nenhum par. */
export interface DrawResult {
  groupId: string;
  status: GroupStatus;
  drawnAt: string;
}

/** Schema `SecretFriend`: só o nome de quem o usuário da sessão tirou. */
export interface SecretFriend {
  name: string;
}

export function drawGroup(id: string): Promise<DrawResult> {
  return apiFetch<DrawResult>(`/groups/${encodeURIComponent(id)}/draw`, { method: 'POST' });
}

export function getMySecretFriend(id: string, signal?: AbortSignal): Promise<SecretFriend> {
  return apiFetch<SecretFriend>(`/groups/${encodeURIComponent(id)}/my-secret-friend`, { signal });
}

/** O usuário da sessão sai do grupo (só antes do sorteio). */
export async function leaveGroup(id: string): Promise<void> {
  await apiFetch(`/groups/${encodeURIComponent(id)}/participants/me`, { method: 'DELETE' });
}

/** O organizador remove um participante (só antes do sorteio). */
export async function removeParticipant(id: string, userId: string): Promise<void> {
  await apiFetch(`/groups/${encodeURIComponent(id)}/participants/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
  });
}
