import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router';
import { api, ApiError, type ChatMessage } from '../api';
import { useClinic } from '../hooks';
import { useT } from '../i18n';
import { internalPath, linkify, mergeMessages } from '../lib/chat';

const SESSION_KEY = 'eldar.chat.session';
const POLL_OPEN_MS = 5_000;
const POLL_CLOSED_MS = 20_000;
const MAX_LENGTH = 1000;

function readSession(): string | null {
  try {
    return localStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
}

function saveSession(id: string) {
  try {
    localStorage.setItem(SESSION_KEY, id);
  } catch {
    // приватный режим браузера: разговор проживёт до перезагрузки страницы
  }
}

/** Плавающий чат с ИИ-помощником клиники; когда разговор ведёт администратор, ответы подтягиваются опросом. */
export function ChatWidget() {
  const t = useT();
  const clinic = useClinic();
  const [open, setOpen] = useState(false);
  const [session, setSession] = useState<string | null>(readSession);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [operator, setOperator] = useState(false);
  const [draft, setDraft] = useState('');
  const [honeypot, setHoneypot] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unread, setUnread] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const lastId = messages.reduce((max, m) => (m.id > max ? m.id : max), 0);

  const poll = useCallback(async () => {
    if (!session) return;
    try {
      const state = await api.chatPoll(session, lastId);
      setOperator(state.operator);
      if (state.messages.length > 0) {
        setMessages((current) => mergeMessages(current, state.messages));
        if (!open) setUnread((n) => n + state.messages.filter((m) => m.role !== 'USER').length);
      }
    } catch {
      // сеть пропала — попробуем при следующем опросе
    }
  }, [session, lastId, open]);

  useEffect(() => {
    if (!session) return;
    api
      .chatPoll(session, 0)
      .then((state) => {
        setOperator(state.operator);
        setMessages(state.messages);
      })
      .catch(() => setSession(null));
  }, []);

  useEffect(() => {
    if (!session || (!open && !operator)) return;
    const timer = setInterval(poll, open ? POLL_OPEN_MS : POLL_CLOSED_MS);
    return () => clearInterval(timer);
  }, [poll, session, open, operator]);

  useEffect(() => {
    if (!open) return;
    setUnread(0);
    inputRef.current?.focus();
    const onKey = (e: globalThis.KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages.length, sending, open]);

  async function send(text: string) {
    const clean = text.trim().slice(0, MAX_LENGTH);
    if (!clean || sending) return;
    const temp: ChatMessage = { id: -Date.now(), role: 'USER', text: clean, sentAt: new Date().toISOString() };
    setMessages((current) => [...current, temp]);
    setDraft('');
    setError(null);
    setSending(true);
    try {
      const state = await api.chatSend(session, clean, honeypot);
      if (state.sessionId !== session) {
        setSession(state.sessionId);
        saveSession(state.sessionId);
      }
      setOperator(state.operator);
      setMessages((current) => mergeMessages(current.filter((m) => m.id !== temp.id), state.messages));
    } catch (e) {
      setMessages((current) => current.filter((m) => m.id !== temp.id));
      setDraft(clean);
      setError(e instanceof ApiError ? e.message : String(e));
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    send(draft);
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      send(draft);
    }
  }

  const phoneHref = clinic ? 'tel:' + clinic.phone.replace(/[^\d+]/g, '') : undefined;

  return (
    <>
      <button
        type="button"
        className={'chat-fab' + (open ? ' is-hidden' : '')}
        onClick={() => setOpen(true)}
        aria-label={t.chat.open}
      >
        <ChatIcon />
        <span className="chat-fab__label">{t.chat.open}</span>
        {unread > 0 && <span className="chat-fab__badge">{unread}</span>}
      </button>

      {open && (
        <section className="chat" role="dialog" aria-label={t.chat.title}>
          <header className="chat__header">
            <span className={'chat__avatar' + (operator ? ' chat__avatar--operator' : '')} aria-hidden="true">
              {operator ? <PersonIcon /> : <SparkIcon />}
            </span>
            <div className="chat__heading">
              <div className="chat__title">{t.chat.title}</div>
              <div className="chat__subtitle">
                <span className="chat__dot" />
                {operator ? t.chat.subtitleOperator : t.chat.subtitleAi}
              </div>
            </div>
            <button type="button" className="chat__close" onClick={() => setOpen(false)} aria-label={t.chat.close}>
              <CloseIcon />
            </button>
          </header>

          <div className="chat__body" ref={listRef} aria-live="polite">
            <Bubble role="ASSISTANT" text={t.chat.greeting} />
            {messages.map((m) => (
              <Bubble key={m.id} role={m.role} text={m.text} label={m.role === 'OPERATOR' ? t.chat.operator : undefined} />
            ))}
            {sending && (
              <div className="bubble bubble--in bubble--typing" aria-label={t.chat.typing}>
                <span />
                <span />
                <span />
              </div>
            )}
            {operator && !sending && messages.at(-1)?.role !== 'OPERATOR' && (
              <div className="chat__note">{t.chat.operatorNote}</div>
            )}
            {error && <div className="chat__error">{error}</div>}
            {messages.length === 0 && !sending && (
              <div className="chat__chips">
                {t.chat.suggestions.map((s) => (
                  <button key={s} type="button" className="chip" onClick={() => send(s)}>
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>

          <form className="chat__form" onSubmit={onSubmit}>
            <input
              className="trap"
              type="text"
              tabIndex={-1}
              autoComplete="off"
              name="website"
              value={honeypot}
              onChange={(e) => setHoneypot(e.target.value)}
              aria-hidden="true"
            />
            <textarea
              ref={inputRef}
              className="chat__input"
              rows={1}
              maxLength={MAX_LENGTH}
              placeholder={t.chat.placeholder}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={onKeyDown}
            />
            <button type="submit" className="chat__send" disabled={!draft.trim() || sending} aria-label={t.chat.send}>
              <SendIcon />
            </button>
          </form>
          <div className="chat__disclaimer">
            {t.chat.disclaimer} {clinic && <a href={phoneHref}>{clinic.phone}</a>}
          </div>
        </section>
      )}
    </>
  );
}

function Bubble({ role, text, label }: { role: ChatMessage['role']; text: string; label?: string }) {
  const mine = role === 'USER';
  return (
    <div className={'bubble ' + (mine ? 'bubble--out' : 'bubble--in') + (role === 'OPERATOR' ? ' bubble--operator' : '')}>
      {label && <div className="bubble__label">{label}</div>}
      {linkify(text).map((s, i) => {
        if (s.kind === 'text') return <span key={i}>{s.value}</span>;
        const path = internalPath(s.href, window.location.origin);
        return path ? (
          <Link key={i} to={path}>
            {s.value}
          </Link>
        ) : (
          <a key={i} href={s.href} target="_blank" rel="noopener noreferrer">
            {s.value}
          </a>
        );
      })}
    </div>
  );
}

function ChatIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.2 3.6c-.5.4-1.3.1-1.3-.6V16A2.5 2.5 0 0 1 4 13.5v-8Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M8.5 9.5h.01M12 9.5h.01M15.5 9.5h.01" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  );
}

function SparkIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 2.5c.4 4.6 2.9 7.1 7.5 7.5-4.6.4-7.1 2.9-7.5 7.5-.4-4.6-2.9-7.1-7.5-7.5 4.6-.4 7.1-2.9 7.5-7.5Z" />
      <path d="M19 15.5c.2 2 1.1 2.9 3 3-1.9.2-2.8 1.1-3 3-.2-1.9-1.1-2.8-3-3 1.9-.1 2.8-1 3-3Z" opacity=".7" />
    </svg>
  );
}

function PersonIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="8" r="3.6" stroke="currentColor" strokeWidth="1.8" />
      <path d="M5 20c.8-3.6 3.5-5.5 7-5.5s6.2 1.9 7 5.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

function SendIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M4 12 20 4l-6.5 16-2.3-6.2L4 12Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </svg>
  );
}
