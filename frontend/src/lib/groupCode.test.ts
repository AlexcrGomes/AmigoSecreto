import { describe, expect, it } from 'vitest';
import { CODE_ALPHABET, formatCode, maskCode, normalizeCode } from './groupCode';

describe('groupCode (mesmas regras do backend)', () => {
  it('alfabeto de 31 símbolos, sem 0 O 1 I L', () => {
    expect(CODE_ALPHABET).toHaveLength(31);
    for (const ambiguous of ['0', 'O', '1', 'I', 'L']) {
      expect(CODE_ALPHABET).not.toContain(ambiguous);
    }
  });

  it('formata com hífen no meio', () => {
    expect(formatCode('K7PQM2XA')).toBe('K7PQ-M2XA');
  });

  it.each([
    [' k7pq-m2xa ', 'K7PQM2XA'],
    ['K7PQ M2XA', 'K7PQM2XA'],
    ['K7PQM2XA', 'K7PQM2XA'],
  ])('normaliza "%s" → %s', (input, expected) => {
    expect(normalizeCode(input)).toBe(expected);
  });

  it.each([null, undefined, '', 'K7PQM2X', 'K7PQM2XAB', 'K7PQ-M2X0', 'K7PQ-M2XI', 'K7PQ_M2XA'])(
    'entrada inválida %s → null',
    (input) => {
      expect(normalizeCode(input)).toBeNull();
    },
  );

  it.each([
    ['k', 'K'],
    ['k7pq', 'K7PQ'],
    ['k7pqm', 'K7PQ-M'],
    ['k7pq-m2xa', 'K7PQ-M2XA'],
    ['k7 pq.m2_xa99', 'K7PQ-M2XA'],
  ])('máscara: "%s" → "%s"', (input, expected) => {
    expect(maskCode(input)).toBe(expected);
  });
});
