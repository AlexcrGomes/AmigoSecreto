import { apiFetch } from './client';
import type { MyDependent } from './groups';

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
