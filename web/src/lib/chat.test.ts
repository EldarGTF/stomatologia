import { describe, expect, it } from 'vitest';
import { internalPath, linkify, mergeMessages } from './chat';

describe('linkify', () => {
  it('выделяет ссылку и не захватывает точку в конце предложения', () => {
    expect(linkify('Ваша запись: https://eldar.kz/booking/abc123. Ждём вас!')).toEqual([
      { kind: 'text', value: 'Ваша запись: ' },
      { kind: 'link', value: 'https://eldar.kz/booking/abc123', href: 'https://eldar.kz/booking/abc123' },
      { kind: 'text', value: '. Ждём вас!' },
    ]);
  });

  it('текст без ссылок остаётся одним куском', () => {
    expect(linkify('Чистка — 15 000 ₸')).toEqual([{ kind: 'text', value: 'Чистка — 15 000 ₸' }]);
  });

  it('ссылка в скобках и в конце текста', () => {
    const segments = linkify('(http://localhost:8080/booking/x)');
    expect(segments[1]).toEqual({ kind: 'link', value: 'http://localhost:8080/booking/x', href: 'http://localhost:8080/booking/x' });
    expect(segments[2]).toEqual({ kind: 'text', value: ')' });
  });
});

describe('internalPath', () => {
  it('ссылка на этот же сайт открывается внутри приложения', () => {
    expect(internalPath('http://localhost:8080/booking/x', 'http://localhost:8080')).toBe('/booking/x');
    expect(internalPath('https://2gis.kz/pavlodar', 'http://localhost:8080')).toBeNull();
  });
});

describe('mergeMessages', () => {
  it('убирает дубли и сортирует по id', () => {
    const merged = mergeMessages(
      [{ id: 1 }, { id: 2 }],
      [{ id: 3 }, { id: 2 }],
    );
    expect(merged.map((m) => m.id)).toEqual([1, 2, 3]);
  });
});
