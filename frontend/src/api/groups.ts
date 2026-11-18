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
