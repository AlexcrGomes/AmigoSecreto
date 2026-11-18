import { describe, expect, it } from 'vitest';
import { loginSchema, registerSchema } from './schemas';
import { safeRedirect } from './redirect';

function messages(result: { success: boolean; error?: { issues: { message: string }[] } }) {
  return result.success ? [] : (result.error?.issues.map((issue) => issue.message) ?? []);
}

describe('registerSchema', () => {
  it('aceita dados válidos e tira espaços do nome e do e-mail', () => {
    const result = registerSchema.parse({
      name: '  Ana  ',
      email: ' ana@x.com ',
      password: 'senha-forte',
    });

    expect(result).toEqual({ name: 'Ana', email: 'ana@x.com', password: 'senha-forte' });
  });

  it('nome com menos de 2 ou mais de 80 caracteres após trim', () => {
    const base = { email: 'ana@x.com', password: 'senha-forte' };
    expect(messages(registerSchema.safeParse({ ...base, name: ' a ' }))).toContain(
      'Informe seu nome (2 a 80 caracteres).',
    );
    expect(messages(registerSchema.safeParse({ ...base, name: 'a'.repeat(81) }))).toContain(
      'Informe seu nome (2 a 80 caracteres).',
    );
  });

  it('e-mail inválido ou com mais de 254 caracteres', () => {
    const base = { name: 'Ana', password: 'senha-forte' };
    expect(messages(registerSchema.safeParse({ ...base, email: 'ana' }))).toEqual([
      'Informe um e-mail válido.',
    ]);
    expect(
      messages(registerSchema.safeParse({ ...base, email: `${'a'.repeat(250)}@x.com` })),
    ).toContain('Informe um e-mail válido.');
  });

  it('senha de 8 a 64 caracteres e no máximo 72 bytes', () => {
    const base = { name: 'Ana', email: 'ana@x.com' };
    expect(messages(registerSchema.safeParse({ ...base, password: '1234567' }))).toEqual([
      'A senha deve ter entre 8 e 64 caracteres.',
    ]);
    expect(messages(registerSchema.safeParse({ ...base, password: 'x'.repeat(65) }))).toEqual([
      'A senha deve ter entre 8 e 64 caracteres.',
    ]);
    expect(messages(registerSchema.safeParse({ ...base, password: 'é'.repeat(40) }))).toEqual([
      'A senha deve ter no máximo 72 bytes (acentos e emojis ocupam mais de um).',
    ]);
  });
});

describe('loginSchema', () => {
  it('exige e-mail válido e senha', () => {
    expect(messages(loginSchema.safeParse({ email: '', password: '' }))).toEqual([
      'Informe um e-mail válido.',
      'Informe a senha.',
    ]);
  });
});

describe('safeRedirect', () => {
  it.each([
    [null, '/'],
    ['', '/'],
    ['/grupos/1?x=1', '/grupos/1?x=1'],
    ['//malicioso.example', '/'],
    ['/\\malicioso.example', '/'],
    ['https://malicioso.example', '/'],
    ['grupos', '/'],
  ])('%s → %s', (input, expected) => {
    expect(safeRedirect(input)).toBe(expected);
  });
});
