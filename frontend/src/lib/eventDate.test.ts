import { describe, expect, it } from 'vitest';
import { formatEventDate, isPastDate, isPastTimeToday, todayInBrasilia } from './eventDate';

/** 15:00 UTC = 12:00 em Brasília. */
const NOON_IN_BRASILIA = new Date('2026-11-19T15:00:00Z');

describe('eventDate (mesmas regras do EventSchedule do backend)', () => {
  it('hoje é a data em Brasília, não em UTC', () => {
    expect(todayInBrasilia(NOON_IN_BRASILIA)).toBe('2026-11-19');
    // 01:30 UTC do dia 20 ainda é 22:30 do dia 19 em Brasília
    expect(todayInBrasilia(new Date('2026-12-20T01:30:00Z'))).toBe('2026-12-19');
  });

  it('só datas antes de hoje são passadas', () => {
    expect(isPastDate('2026-11-18', NOON_IN_BRASILIA)).toBe(true);
    expect(isPastDate('2026-11-19', NOON_IN_BRASILIA)).toBe(false);
    expect(isPastDate('2026-11-20', NOON_IN_BRASILIA)).toBe(false);
  });

  it('horário passado só conta quando a data é hoje', () => {
    expect(isPastTimeToday('2026-11-19', '11:59', NOON_IN_BRASILIA)).toBe(true);
    expect(isPastTimeToday('2026-11-19', '12:00', NOON_IN_BRASILIA)).toBe(true);
    expect(isPastTimeToday('2026-11-19', '12:01', NOON_IN_BRASILIA)).toBe(false);
    expect(isPastTimeToday('2026-11-20', '08:00', NOON_IN_BRASILIA)).toBe(false);
  });

  it('formata a data por extenso, com ou sem horário', () => {
    expect(formatEventDate('2026-12-19', '19:00')).toBe('sábado, 19 de dezembro de 2026, às 19:00');
    expect(formatEventDate('2026-12-19', null)).toBe('sábado, 19 de dezembro de 2026');
    expect(formatEventDate('2027-01-01')).toBe('sexta-feira, 1 de janeiro de 2027');
  });
});
