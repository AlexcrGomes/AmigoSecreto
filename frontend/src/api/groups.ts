import { apiFetch } from './client';

/** Tipos dos schemas de contracts/openapi.yaml (feature 001 e as mudanças da 002 à 004). */
export type GroupStatus = 'OPEN' | 'DRAWN' | 'FINISHED';

/** Detalhes do evento. Data `AAAA-MM-DD` e horário `HH:mm` em horário de Brasília. */
export interface EventDetails {
  description: string | null;
  eventDate: string;
  eventTime: string | null;
  location: string;
  /** Valor do presente em reais inteiros (feature 003); `null` ou ausente = não informado. */
  giftValue?: number | null;
}

/** Item da própria lista de desejos (feature 003). */
export interface WishItem {
  id: string;
  description: string;
  url: string | null;
}

/** A lista de desejos do usuário da sessão no grupo: até `maxItems` itens, na ordem de cadastro. */
export interface MyWishlist {
  items: WishItem[];
  maxItems: number;
  /** `false` em grupo finalizado. */
  editable: boolean;
}

/** Item da lista de quem o usuário tirou: sem id e sem dono (research R27 da 003). */
export interface FriendWishItem {
  description: string;
  url: string | null;
}

/** Por que o sorteio ainda não pode ser feito (research R16), ou `null` se pode. */
export type DrawBlockedReason =
  | 'event-details-required'
  | 'event-date-passed'
  | 'not-enough-participants'
  | 'participants-pending'
  | 'no-valid-draw'
  | null;

/** Por que o usuário não pode adicionar dependentes agora (feature 004), ou `null` se pode. */
export type DependentsBlockedReason =
  | 'group-already-drawn'
  | 'group-finished'
  | 'group-full'
  | 'dependents-limit'
  | 'dependents-locked'
  | null;

/** Pedido de saída pendente, depois do sorteio. */
export interface ExitRequest {
  id: string;
  userId: string;
  name: string;
  requestedAt: string;
  /** Nome do responsável quando quem sai é um dependente (feature 004). */
  guardianName?: string | null;
}

export interface GroupSummary {
  id: string;
  name: string;
  status: GroupStatus;
  participantCount: number;
  organizer: boolean;
  /** `null` em grupos antigos que ainda esperam a data. */
  eventDate: string | null;
  eventTime: string | null;
}

export interface Participant {
  userId: string;
  name: string;
  organizer: boolean;
  confirmed: boolean;
  /** Dependente de outro participante (feature 004): mostrado como "Lucas (dependente de Ana)". */
  dependent?: boolean;
  guardianName?: string | null;
}

/** Um dependente do usuário da sessão (feature 004) — nunca o resultado do sorteio dele. */
export interface MyDependent {
  id: string;
  name: string;
  /** Espelho da confirmação do responsável (research R36). */
  confirmed: boolean;
  wishlist: MyWishlist;
  /** Pedido de saída pendente do dependente, se houver. */
  exitRequest: ExitRequest | null;
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
  /** `null` em grupo antigo ainda sem data e local (FR-034). */
  eventDetails: EventDetails | null;
  eventDetailsMissing: boolean;
  /** Grupo aberto cujo evento já aconteceu: o sorteio fica bloqueado até nova data. */
  eventDatePassed: boolean;
  /** Enviar ao confirmar (FR-035). */
  detailsVersion: number;
  confirmedCount: number;
  myConfirmation: 'PENDING' | 'CONFIRMED';
  drawBlockedReason: DrawBlockedReason;
  finishedAt: string | null;
  /** O último sorteio foi desfeito por uma saída aprovada. */
  drawUndone: boolean;
  myExitRequest: ExitRequest | null;
  /** Só para o organizador; vazio para os demais. */
  pendingExitRequests: ExitRequest[];
  /** A lista de desejos do próprio usuário — nunca a de outra pessoa (feature 003). */
  myWishlist: MyWishlist;
  /** Só os dependentes do usuário da sessão (feature 004). */
  myDependents: MyDependent[];
  maxDependents: number;
  canAddDependents: boolean;
  dependentsBlockedReason: DependentsBlockedReason;
}

export function listGroups(signal?: AbortSignal): Promise<GroupSummary[]> {
  return apiFetch<GroupSummary[]>('/groups', { signal });
}

/** Corpo de criar e de editar os detalhes do evento (schema `EventDetailsRequest`). */
export interface EventDetailsBody {
  description: string | null;
  eventDate: string;
  eventTime: string | null;
  location: string;
  /** Valor do presente em reais inteiros, ou `null` (feature 003); só muda antes do sorteio. */
  giftValue?: number | null;
}

export function createGroup(name: string, details: EventDetailsBody): Promise<GroupDetail> {
  return apiFetch<GroupDetail>('/groups', { method: 'POST', body: { name, ...details } });
}

/** O organizador altera descrição, data, horário e local. */
export function updateEventDetails(id: string, details: EventDetailsBody): Promise<GroupDetail> {
  return apiFetch<GroupDetail>(`/groups/${encodeURIComponent(id)}/event-details`, {
    method: 'PUT',
    body: details,
  });
}

/**
 * O usuário da sessão confirma a própria participação na versão dos detalhes que viu (FR-009, FR-035).
 * Se os detalhes mudaram nesse meio-tempo, responde 409 `details-changed`.
 */
export function confirmParticipation(id: string, detailsVersion: number): Promise<GroupDetail> {
  return apiFetch<GroupDetail>(`/groups/${encodeURIComponent(id)}/confirmation`, {
    method: 'POST',
    body: { detailsVersion },
  });
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
  /** Lista de desejos de quem o usuário tirou, na ordem de cadastro (feature 003). */
  wishlist: FriendWishItem[];
  /** Última alteração da lista, só se foi depois do sorteio; senão `null`. */
  wishlistUpdatedAt: string | null;
}

/** Resultado de um dependente do usuário da sessão (feature 004, research R38). */
export interface DependentSecretFriend {
  /** O dependente — não quem ele tirou. */
  dependentId: string;
  dependentName: string;
  secretFriend: SecretFriend;
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
