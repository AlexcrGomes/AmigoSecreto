/**
 * Datas do evento em horário de Brasília, como o backend (research R13, R14 e R23 da feature 002). A API
 * fala `eventDate` (`AAAA-MM-DD`) e `eventTime` (`HH:mm`); aqui só se compara e se formata. Quem decide é
 * o servidor: estas regras existem para mostrar o erro na hora.
 */

const EVENT_ZONE = 'America/Sao_Paulo';

const dateParts = new Intl.DateTimeFormat('en-CA', {
  timeZone: EVENT_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const timeParts = new Intl.DateTimeFormat('en-GB', {
  timeZone: EVENT_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const longDate = new Intl.DateTimeFormat('pt-BR', {
  timeZone: EVENT_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

function part(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): string {
  return parts.find((p) => p.type === type)?.value ?? '';
}

/** A data de hoje em Brasília, no formato `AAAA-MM-DD`. */
export function todayInBrasilia(now: Date = new Date()): string {
  const parts = dateParts.formatToParts(now);
  return `${part(parts, 'year')}-${part(parts, 'month')}-${part(parts, 'day')}`;
}

/** A data (`AAAA-MM-DD`) é anterior a hoje em Brasília. */
export function isPastDate(date: string, now: Date = new Date()): boolean {
  return date < todayInBrasilia(now);
}

/** A data é hoje e o horário (`HH:mm`) já chegou — o minuto atual conta como passado. */
export function isPastTimeToday(date: string, time: string, now: Date = new Date()): boolean {
  if (date !== todayInBrasilia(now)) return false;
  const parts = timeParts.formatToParts(now);
  return time <= `${part(parts, 'hour')}:${part(parts, 'minute')}`;
}

/** "sábado, 19 de dezembro de 2026, às 19:00", ou sem o horário quando ele não foi informado. */
export function formatEventDate(date: string, time?: string | null): string {
  const [year = 0, month = 1, day = 1] = date.split('-').map(Number);
  // Meio-dia UTC cai no mesmo dia em Brasília: o dia da semana sai certo
  const formatted = longDate.format(new Date(Date.UTC(year, month - 1, day, 12)));
  return time ? `${formatted}, às ${time}` : formatted;
}
