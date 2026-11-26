import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '../api/client';
import type { GroupDetail } from '../api/groups';

export const TOO_MANY_ATTEMPTS = 'Muitas tentativas. Tente novamente em alguns minutos.';
const UNKNOWN_ERROR = 'Algo deu errado. Tente novamente.';

/** A confirmação foi recusada porque os detalhes mudaram depois de a tela carregar (FR-035). */
export const DETAILS_CHANGED = 'Os detalhes mudaram. Confira e confirme de novo.';
/** Aviso a quem não é organizador quando a data, o horário ou o local mudaram (FR-015). */
export const CONFIRMATIONS_RESET = 'Os detalhes do evento mudaram. Confira e confirme de novo.';

/** Responsáveis e dependentes não permitem nenhum sorteio (FR-009b da 004). */
export const NO_VALID_DRAW =
  'Com os dependentes deste grupo, não há sorteio possível. Convide mais participantes.';

/** O problema é do tipo informado (ex.: `details-changed`)? */
export function isProblem(error: unknown, type: string): boolean {
  return error instanceof ApiError && error.type === `/problems/${type}`;
}

/** Por que o botão de sortear está desabilitado (contracts/ui-routes.md), ou `null`. */
export function drawBlockedMessage(group: GroupDetail): string | null {
  switch (group.drawBlockedReason) {
    case 'participants-pending': {
      // Só quem tem conta: os dependentes confirmam junto com o responsável (feature 004)
      const pending = group.participants.filter(
        (participant) => !participant.confirmed && !participant.dependent,
      );
      const verb = pending.length === 1 ? 'Falta' : 'Faltam';
      return `${verb} ${pending.length} confirmar: ${pending.map((p) => p.name).join(', ')}`;
    }
    case 'no-valid-draw':
      return NO_VALID_DRAW;
    case 'not-enough-participants':
      return 'São necessários pelo menos 3 participantes';
    case 'event-date-passed':
      return 'A data do evento já passou. Atualize a data para sortear.';
    case 'event-details-required':
      return 'Informe a data e o local do evento para sortear.';
    default:
      return null;
  }
}

/** Mensagem para o usuário a partir de qualquer erro de uma ação (FR-035). */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status === 429 ? TOO_MANY_ATTEMPTS : error.detail;
  }
  return UNKNOWN_ERROR;
}

/** Leva os erros de campo do servidor (`errors[{field,message}]`) para o formulário. */
export function applyFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): void {
  if (!(error instanceof ApiError)) return;
  for (const field of fields) {
    const message = error.fieldError(field);
    if (message) setError(field, { type: 'server', message });
  }
}
