import { describe, expect, it } from 'vitest';
import {
  brandName, dayPart, dayRange, formatPhone, initials, isoDate, isPhoneComplete, money, parseDateTime,
  phoneDigits, plural, relativeDay, shortName, slotLabel, uniqueByStart,
} from './format';

describe('деньги и числа', () => {
  it('разделяет тысячи неразрывным пробелом', () => {
    expect(money(27500)).toBe('27\u00a0500\u00a0₸');
    expect(money(175000)).toBe('175\u00a0000\u00a0₸');
    expect(money(900)).toBe('900\u00a0₸');
  });

  it('склоняет слова по числу', () => {
    const windows = (n: number) => plural(n, 'окно', 'окна', 'окон');
    expect(windows(1)).toBe('окно');
    expect(windows(3)).toBe('окна');
    expect(windows(11)).toBe('окон');
    expect(windows(21)).toBe('окно');
    expect(windows(25)).toBe('окон');
  });
});

describe('даты', () => {
  const today = new Date(2026, 9, 9);

  it('разбирает время сервера как местное', () => {
    const d = parseDateTime('2026-10-12T11:30:00');
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()]).toEqual([2026, 9, 12, 11, 30]);
  });

  it('строит ленту дней через границу месяца', () => {
    const days = dayRange(new Date(2026, 9, 30), 3).map(isoDate);
    expect(days).toEqual(['2026-10-30', '2026-10-31', '2026-11-01']);
  });

  it('называет ближайшие дни по-человечески', () => {
    expect(relativeDay(new Date(2026, 9, 9), today)).toBe('сегодня');
    expect(relativeDay(new Date(2026, 9, 10), today)).toBe('завтра');
    expect(relativeDay(new Date(2026, 9, 12), today)).toBe('пн, 12 октября');
    expect(slotLabel('2026-10-10T09:15:00', today)).toBe('завтра в 09:15');
  });

  it('делит день на утро, день и вечер', () => {
    expect(dayPart('2026-10-12T09:00:00')).toBe('morning');
    expect(dayPart('2026-10-12T12:00:00')).toBe('day');
    expect(dayPart('2026-10-12T17:30:00')).toBe('evening');
  });

  it('оставляет одно окно на одно время у «любого врача»', () => {
    const slots = [
      { start: '2026-10-12T10:00:00', doctorId: 1 },
      { start: '2026-10-12T10:00:00', doctorId: 2 },
      { start: '2026-10-12T10:30:00', doctorId: 2 },
    ];
    expect(uniqueByStart(slots).map((s) => s.doctorId)).toEqual([1, 2]);
  });
});

describe('телефон', () => {
  it('принимает номер в любом привычном виде', () => {
    expect(phoneDigits('8 (701) 555-12-34')).toBe('7015551234');
    expect(phoneDigits('+7 701 555 12 34')).toBe('7015551234');
    expect(phoneDigits('7015551234')).toBe('7015551234');
  });

  it('форматирует по мере ввода', () => {
    expect(formatPhone('7')).toBe('+7 (7');
    expect(formatPhone('701')).toBe('+7 (701)');
    expect(formatPhone('7015')).toBe('+7 (701) 5');
    expect(formatPhone('70155512')).toBe('+7 (701) 555-12');
    expect(formatPhone('87015551234')).toBe('+7 (701) 555-12-34');
    expect(formatPhone('')).toBe('');
  });

  it('проверяет, что номер набран полностью', () => {
    expect(isPhoneComplete('+7 (701) 555-12-34')).toBe(true);
    expect(isPhoneComplete('+7 (701) 555-12')).toBe(false);
  });
});

describe('имена', () => {
  it('сокращает ФИО врача и берёт инициалы', () => {
    expect(shortName('Иванова Елена Петровна')).toBe('Иванова Е. П.');
    expect(initials('Иванова Елена Петровна')).toBe('ИЕ');
  });

  it('берёт короткое название клиники из кавычек', () => {
    expect(brandName('Стоматологическая клиника «Eldar»')).toBe('Eldar');
    expect(brandName('Улыбка')).toBe('Улыбка');
  });
});
