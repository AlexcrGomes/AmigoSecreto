import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '../api/client';

export const TOO_MANY_ATTEMPTS = 'Muitas tentativas. Tente novamente em alguns minutos.';
const UNKNOWN_ERROR = 'Algo deu errado. Tente novamente.';

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
