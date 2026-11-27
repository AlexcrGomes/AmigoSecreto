import { apiFetch } from './client';
import type { DependentSecretFriend, ExitRequest, MyDependent, MyWishlist } from './groups';
import type { WishItemBody } from './wishlist';

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

function wishItemsPath(groupId: string, dependentId: string) {
  return `${dependentPath(groupId, dependentId)}/wishlist/items`;
}

/** Adiciona um item à lista de um dependente seu (FR-014); devolve a lista atualizada. */
export function addDependentWishItem(
  groupId: string,
  dependentId: string,
  body: WishItemBody,
): Promise<MyWishlist> {
  return apiFetch<MyWishlist>(wishItemsPath(groupId, dependentId), { method: 'POST', body });
}

/** Altera um item da lista de um dependente seu; devolve a lista atualizada. */
export function updateDependentWishItem(
  groupId: string,
  dependentId: string,
  itemId: string,
  body: WishItemBody,
): Promise<MyWishlist> {
  return apiFetch<MyWishlist>(
    `${wishItemsPath(groupId, dependentId)}/${encodeURIComponent(itemId)}`,
    { method: 'PUT', body },
  );
}

/** Remove um item da lista de um dependente seu. */
export async function removeDependentWishItem(
  groupId: string,
  dependentId: string,
  itemId: string,
): Promise<void> {
  await apiFetch(`${wishItemsPath(groupId, dependentId)}/${encodeURIComponent(itemId)}`, {
    method: 'DELETE',
  });
}

/** Pede a saída de um dependente seu depois do sorteio (FR-015); o organizador decide. */
export function requestDependentExit(groupId: string, dependentId: string): Promise<ExitRequest> {
  return apiFetch<ExitRequest>(`${dependentPath(groupId, dependentId)}/exit-request`, {
    method: 'POST',
  });
}

/** Cancela o pedido de saída pendente de um dependente seu. */
export async function cancelDependentExit(groupId: string, dependentId: string): Promise<void> {
  await apiFetch(`${dependentPath(groupId, dependentId)}/exit-request`, { method: 'DELETE' });
}
