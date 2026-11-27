import { apiFetch } from './client';
import type { DependentSecretFriend, MyDependent } from './groups';

/** Rotas dos dependentes do usuário da sessão (contracts/openapi.yaml da 004). */
function dependentsPath(groupId: string) {
  return `/groups/${encodeURIComponent(groupId)}/my-dependents`;
}

function dependentPath(groupId: string, dependentId: string) {
  return `${dependentsPath(groupId)}/${encodeURIComponent(dependentId)}`;
}

/** Adiciona um dependente; o usuário da sessão vira o responsável (FR-001). */
export function addDependent(groupId: string, name: string): Promise<MyDependent> {
  return apiFetch<MyDependent>(dependentsPath(groupId), { method: 'POST', body: { name } });
}

/** Renomeia um dependente seu (FR-006). */
export function renameDependent(
  groupId: string,
  dependentId: string,
  name: string,
): Promise<MyDependent> {
  return apiFetch<MyDependent>(dependentPath(groupId, dependentId), {
    method: 'PUT',
    body: { name },
  });
}

/** Remove um dependente seu (FR-006). */
export async function removeDependent(groupId: string, dependentId: string): Promise<void> {
  await apiFetch(dependentPath(groupId, dependentId), { method: 'DELETE' });
}

/**
 * O amigo secreto de cada dependente do usuário da sessão (FR-010 da 004). Sem id: o servidor usa os dependentes
 * de que o usuário da sessão é responsável (research R38).
 */
export function getDependentsSecretFriends(
  groupId: string,
  signal?: AbortSignal,
): Promise<DependentSecretFriend[]> {
  return apiFetch<DependentSecretFriend[]>(`${dependentsPath(groupId)}/secret-friends`, { signal });
}
