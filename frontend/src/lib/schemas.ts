import { z } from 'zod';
import { isPastDate, isPastTimeToday } from './eventDate';

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

export const GROUP_NAME_MESSAGE = 'Dê um nome ao grupo (até 60 caracteres).';

/** Mensagens dos detalhes do evento, iguais às do backend (data-model.md da 002, "Validações"). */
export const EVENT_MESSAGES = {
  description: 'A descrição pode ter até 500 caracteres.',
  dateRequired: 'Informe a data do evento.',
  datePast: 'A data do evento precisa ser hoje ou no futuro.',
  timeFormat: 'Informe o horário no formato HH:mm.',
  timePast: 'Esse horário já passou.',
  location: 'Informe o local do evento (até 120 caracteres).',
  giftValue: 'Informe um valor inteiro entre R$ 1 e R$ 100.000.',
} as const;

/** Vazio ou inteiro de 1 a 100.000 (valor do presente, feature 003). */
function isGiftValue(value: string): boolean {
  if (value === '') return true;
  if (!/^\d+$/.test(value)) return false;
  const amount = Number(value);
  return amount >= 1 && amount <= 100_000;
}

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

const groupName = z.string().trim().min(1, GROUP_NAME_MESSAGE).max(60, GROUP_NAME_MESSAGE);

/**
 * Formulário de detalhes do evento (com o nome do grupo na criação). Data e horário vazios são `''`.
 * Ao editar, a data só é validada se data ou horário mudarem (research R14): `original` traz os valores
 * salvos.
 */
export function eventDetailsSchema(
  options: { withName?: boolean; original?: { eventDate: string; eventTime: string } } = {},
) {
  const { withName = false, original } = options;
  return z
    .object({
      name: withName ? groupName : z.string(),
      description: z.string().trim().max(500, EVENT_MESSAGES.description),
      eventDate: z.string().min(1, EVENT_MESSAGES.dateRequired),
      eventTime: z
        .string()
        .refine((value) => value === '' || TIME_PATTERN.test(value), EVENT_MESSAGES.timeFormat),
      location: z.string().trim().min(1, EVENT_MESSAGES.location).max(120, EVENT_MESSAGES.location),
      giftValue: z.string().trim().refine(isGiftValue, EVENT_MESSAGES.giftValue),
    })
    .superRefine((values, ctx) => {
      if (!values.eventDate) return;
      const unchanged =
        original !== undefined &&
        original.eventDate === values.eventDate &&
        original.eventTime === values.eventTime;
      if (unchanged) return;
      if (isPastDate(values.eventDate)) {
        ctx.addIssue({ code: 'custom', path: ['eventDate'], message: EVENT_MESSAGES.datePast });
      } else if (
        TIME_PATTERN.test(values.eventTime) &&
        isPastTimeToday(values.eventDate, values.eventTime)
      ) {
        ctx.addIssue({ code: 'custom', path: ['eventTime'], message: EVENT_MESSAGES.timePast });
      }
    });
}

export type EventDetailsInput = z.infer<ReturnType<typeof eventDetailsSchema>>;

export const createGroupSchema = eventDetailsSchema({ withName: true });

export type CreateGroupInput = EventDetailsInput;

/** Mensagens do item da lista de desejos, iguais às do backend (data-model.md da 003). */
export const WISH_MESSAGES = {
  description: 'Descreva a sugestão (até 120 caracteres).',
  url: 'Informe um link que comece com http:// ou https://.',
} as const;

const WEB_LINK = /^https?:\/\/[^\s/?#]+[^\s]*$/i;

/** Item da lista de desejos: descrição obrigatória e link opcional `http(s)://` (FR-002, FR-003). */
export const wishItemSchema = z.object({
  description: z
    .string()
    .trim()
    .min(1, WISH_MESSAGES.description)
    .max(120, WISH_MESSAGES.description),
  url: z
    .string()
    .trim()
    .max(500, WISH_MESSAGES.url)
    .refine((value) => value === '' || WEB_LINK.test(value), WISH_MESSAGES.url),
});

export type WishItemInput = z.infer<typeof wishItemSchema>;
