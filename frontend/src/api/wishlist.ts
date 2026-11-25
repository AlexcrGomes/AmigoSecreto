import { apiFetch } from './client';
import type { MyWishlist } from './groups';

/** Corpo de criar e de alterar um item (schema `WishItemRequest` da 003). */
export interface WishItemBody {
  description: string;
  url: string | null;
}

function itemsPath(groupId: string) {
  return `/groups/${encodeURIComponent(groupId)}/wishlist/mine/items`;
}

/** Adiciona um item à própria lista; devolve a lista atualizada. */
export function addWishItem(groupId: string, body: WishItemBody): Promise<MyWishlist> {
  return apiFetch<MyWishlist>(itemsPath(groupId), { method: 'POST', body });
}

/** Altera um item da própria lista; devolve a lista atualizada. */
export function updateWishItem(
  groupId: string,
  itemId: string,
  body: WishItemBody,
): Promise<MyWishlist> {
  return apiFetch<MyWishlist>(`${itemsPath(groupId)}/${encodeURIComponent(itemId)}`, {
    method: 'PUT',
    body,
  });
}

/** Remove um item da própria lista. */
export async function removeWishItem(groupId: string, itemId: string): Promise<void> {
  await apiFetch(`${itemsPath(groupId)}/${encodeURIComponent(itemId)}`, { method: 'DELETE' });
}
