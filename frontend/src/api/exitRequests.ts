import { apiFetch } from './client';
import type { ExitRequest, GroupDetail } from './groups';

/** Schema `ExitRequestNotice`: decisão sobre um pedido de saída ainda não vista (research R20). */
export interface ExitRequestNotice {
  id: string;
  groupId: string;
  groupName: string;
  status: 'APPROVED' | 'REJECTED' | 'CANCELLED';
  decidedAt: string;
}

function groupPath(groupId: string) {
  return `/groups/${encodeURIComponent(groupId)}/exit-requests`;
}

/** O usuário da sessão pede para sair de um grupo sorteado (FR-025). */
export function requestExit(groupId: string): Promise<ExitRequest> {
  return apiFetch<ExitRequest>(groupPath(groupId), { method: 'POST' });
}

/** O usuário da sessão cancela o próprio pedido pendente. */
export async function cancelExitRequest(groupId: string): Promise<void> {
  await apiFetch(`${groupPath(groupId)}/mine`, { method: 'DELETE' });
}

/** O organizador aprova: a pessoa sai e o sorteio é desfeito (FR-028). */
export function approveExitRequest(groupId: string, requestId: string): Promise<GroupDetail> {
  return apiFetch<GroupDetail>(`${groupPath(groupId)}/${encodeURIComponent(requestId)}/approve`, {
    method: 'POST',
  });
}

/** O organizador recusa: nada muda no grupo (FR-026). */
export function rejectExitRequest(groupId: string, requestId: string): Promise<GroupDetail> {
  return apiFetch<GroupDetail>(`${groupPath(groupId)}/${encodeURIComponent(requestId)}/reject`, {
    method: 'POST',
  });
}

export function listExitRequestNotices(signal?: AbortSignal): Promise<ExitRequestNotice[]> {
  return apiFetch<ExitRequestNotice[]>('/exit-request-notices', { signal });
}

/** Marca o aviso como visto (idempotente). */
export async function acknowledgeExitRequestNotice(requestId: string): Promise<void> {
  await apiFetch(`/exit-request-notices/${encodeURIComponent(requestId)}/ack`, { method: 'POST' });
}
