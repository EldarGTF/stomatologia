// Разбор ответов чата: ссылки в тексте становятся кликабельными, история хранится по возрастанию id.

export type Segment = { kind: 'text'; value: string } | { kind: 'link'; value: string; href: string };

const URL = /https?:\/\/[^\s<>"«»]+/g;
const TRAILING = /[.,;:!?)\]]+$/;

/** «Ссылка: https://site/booking/abc.» → текст, ссылка (без точки в конце), текст. */
export function linkify(text: string): Segment[] {
  const out: Segment[] = [];
  let last = 0;
  for (const match of text.matchAll(URL)) {
    const raw = match[0];
    const href = raw.replace(TRAILING, '');
    const start = match.index ?? 0;
    if (start > last) out.push({ kind: 'text', value: text.slice(last, start) });
    out.push({ kind: 'link', value: href, href });
    last = start + href.length;
  }
  if (last < text.length) out.push({ kind: 'text', value: text.slice(last) });
  return out;
}

/** Путь внутри сайта, если ссылка ведёт на него же, иначе null: такие ссылки открываются без перезагрузки. */
export function internalPath(href: string, origin: string): string | null {
  if (!href.startsWith(origin + '/')) return null;
  return href.slice(origin.length);
}

/** Добавляет новые сообщения без дублей (ответ на отправку и опрос могут вернуть одно и то же). */
export function mergeMessages<T extends { id: number }>(current: T[], incoming: T[]): T[] {
  if (incoming.length === 0) return current;
  const byId = new Map(current.map((m) => [m.id, m]));
  for (const m of incoming) byId.set(m.id, m);
  return [...byId.values()].sort((a, b) => a.id - b.id);
}
