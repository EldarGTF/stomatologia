// Форматирование для интерфейса: деньги, даты, телефоны. Без зависимостей — легко тестировать.

const MONTHS_GENITIVE = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
];
const WEEKDAYS_SHORT = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];
const WEEKDAYS_FULL = ['воскресенье', 'понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота'];

/** 27500 → «27 500 ₸» (неразрывные пробелы, чтобы сумма не переносилась). */
export function money(amount: number): string {
  const rounded = Math.round(amount);
  return rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '\u00a0') + '\u00a0₸';
}

/** 1 окно, 2 окна, 5 окон. */
export function plural(n: number, one: string, few: string, many: string): string {
  const mod100 = Math.abs(n) % 100;
  const mod10 = mod100 % 10;
  if (mod100 >= 11 && mod100 <= 14) return many;
  if (mod10 === 1) return one;
  if (mod10 >= 2 && mod10 <= 4) return few;
  return many;
}

/** «2026-10-12» → Date в местном времени (new Date("2026-10-12") был бы полуночью по UTC). */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** «2026-10-12T11:30:00» → Date в местном времени клиники. */
export function parseDateTime(iso: string): Date {
  const date = parseDate(iso);
  const [h, min] = iso.slice(11, 16).split(':').map(Number);
  date.setHours(h || 0, min || 0, 0, 0);
  return date;
}

/** Date → «2026-10-12». */
export function isoDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function addDays(d: Date, days: number): Date {
  const copy = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  copy.setDate(copy.getDate() + days);
  return copy;
}

/** Даты подряд начиная с from: для ленты выбора дня. */
export function dayRange(from: Date, count: number): Date[] {
  return Array.from({ length: count }, (_, i) => addDays(from, i));
}

export const time = (iso: string) => iso.slice(11, 16);
export const weekdayShort = (d: Date) => WEEKDAYS_SHORT[d.getDay()];
export const weekdayFull = (d: Date) => WEEKDAYS_FULL[d.getDay()];
export const isWeekend = (d: Date) => d.getDay() === 0 || d.getDay() === 6;

/** «12 октября». */
export function dayMonth(d: Date): string {
  return `${d.getDate()} ${MONTHS_GENITIVE[d.getMonth()]}`;
}

/** «сегодня», «завтра» или «пн, 12 октября». */
export function relativeDay(d: Date, today: Date = new Date()): string {
  const diff = Math.round((parseDate(isoDate(d)).getTime() - parseDate(isoDate(today)).getTime()) / 86_400_000);
  if (diff === 0) return 'сегодня';
  if (diff === 1) return 'завтра';
  return `${weekdayShort(d)}, ${dayMonth(d)}`;
}

/** «завтра в 11:30» / «пн, 12 октября в 11:30». */
export function slotLabel(iso: string, today: Date = new Date()): string {
  return `${relativeDay(parseDateTime(iso), today)} в ${time(iso)}`;
}

export type DayPart = 'morning' | 'day' | 'evening';

/** Утро — до 12:00, день — до 17:00, вечер — после. */
export function dayPart(iso: string): DayPart {
  const hour = Number(iso.slice(11, 13));
  if (hour < 12) return 'morning';
  if (hour < 17) return 'day';
  return 'evening';
}

/** Окна «любого врача» с одинаковым началом показываются одной кнопкой — берётся первое. */
export function uniqueByStart<T extends { start: string }>(slots: T[]): T[] {
  const seen = new Set<string>();
  return slots.filter((s) => (seen.has(s.start) ? false : (seen.add(s.start), true)));
}

/** Только цифры номера без кода страны: «+7 (701) 555-12-34» → «7015551234». */
export function phoneDigits(input: string): string {
  let digits = input.replace(/\D/g, '');
  if (digits.length === 11 && (digits.startsWith('7') || digits.startsWith('8'))) digits = digits.slice(1);
  else if (digits.length > 10 && digits.startsWith('7')) digits = digits.slice(1);
  return digits.slice(0, 10);
}

/** Маска ввода: по мере набора цифр показывает «+7 (701) 555-12-34». */
export function formatPhone(input: string): string {
  const d = phoneDigits(input);
  if (!d) return '';
  let out = '+7 (' + d.slice(0, 3);
  if (d.length >= 3) out += ')';
  if (d.length > 3) out += ' ' + d.slice(3, 6);
  if (d.length > 6) out += '-' + d.slice(6, 8);
  if (d.length > 8) out += '-' + d.slice(8, 10);
  return out;
}

export const isPhoneComplete = (input: string) => phoneDigits(input).length === 10;

/** «Иванова Елена Петровна» → «Иванова Е. П.». */
export function shortName(fullName: string): string {
  const [last, ...rest] = fullName.trim().split(/\s+/);
  return [last, ...rest.map((p) => p[0] + '.')].join(' ');
}

/** «ИЕ» — инициалы для аватара врача. */
export function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  return (parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '');
}

/** «Стоматологическая клиника «Eldar»» → «Eldar»: короткое название для логотипа. */
export function brandName(clinicName: string): string {
  const quoted = clinicName.match(/[«"]([^»"]+)[»"]/);
  return quoted ? quoted[1] : clinicName;
}
