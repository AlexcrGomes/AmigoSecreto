/** Código do grupo — mesmas regras do backend (research R5, GroupCodeGenerator). */
export const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
export const CODE_LENGTH = 8;

/** `K7PQM2XA` → `K7PQ-M2XA`. */
export function formatCode(code: string): string {
  return `${code.slice(0, 4)}-${code.slice(4)}`;
}

/** Entrada do usuário → código sem hífen, ou `null` se não tiver 8 símbolos do alfabeto. */
export function normalizeCode(input: string | null | undefined): string | null {
  if (!input) return null;
  const code = input.replace(/[\s-]/g, '').toUpperCase();
  if (code.length !== CODE_LENGTH) return null;
  for (const char of code) {
    if (!CODE_ALPHABET.includes(char)) return null;
  }
  return code;
}

/** Máscara de digitação `XXXX-XXXX`: maiúsculas, só letras e números, no máximo 8. */
export function maskCode(input: string): string {
  const raw = input
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, CODE_LENGTH);
  return raw.length > 4 ? formatCode(raw) : raw;
}
