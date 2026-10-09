import { ru, type Strings } from './ru';

// Пока сайт только на русском. Для казахского: добавить kk.ts типа Strings, выбор языка в контексте
// и переключатель в шапке — компоненты уже берут тексты только отсюда.
export function useT(): Strings {
  return ru;
}

/** «На {days} дней вперёд» + { days: 60 } → «На 60 дней вперёд». */
export function fill(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key) => String(values[key] ?? ''));
}
