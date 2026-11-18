import { z } from 'zod';

/** Mensagens iguais às do backend (data-model.md, "Mensagens de validação"). */
export const MESSAGES = {
  name: 'Informe seu nome (2 a 80 caracteres).',
  email: 'Informe um e-mail válido.',
  password: 'A senha deve ter entre 8 e 64 caracteres.',
  passwordBytes: 'A senha deve ter no máximo 72 bytes (acentos e emojis ocupam mais de um).',
  passwordRequired: 'Informe a senha.',
} as const;

const PASSWORD_MAX_BYTES = 72;

const email = z.string().trim().max(254, MESSAGES.email).pipe(z.email(MESSAGES.email));

export const registerSchema = z.object({
  name: z.string().trim().min(2, MESSAGES.name).max(80, MESSAGES.name),
  email,
  password: z
    .string()
    .min(8, MESSAGES.password)
    .max(64, MESSAGES.password)
    // O BCrypt só considera 72 bytes; o backend rejeita o que passar disso (research R3)
    .refine(
      (value) => new TextEncoder().encode(value).length <= PASSWORD_MAX_BYTES,
      MESSAGES.passwordBytes,
    ),
});

export const loginSchema = z.object({
  email,
  password: z.string().min(1, MESSAGES.passwordRequired),
});

export type RegisterInput = z.infer<typeof registerSchema>;
export type LoginInput = z.infer<typeof loginSchema>;
