import { useT } from '../i18n';
import { initials } from '../lib/format';

export function Loading({ label }: { label?: string }) {
  const t = useT();
  return (
    <div className="state" role="status">
      <span className="spinner" aria-hidden="true" />
      {label ?? t.common.loading}
    </div>
  );
}

export function ErrorNote({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const t = useT();
  return (
    <div className="note note--error" role="alert">
      <span>{message}</span>
      {onRetry && (
        <button className="btn btn--ghost btn--sm" onClick={onRetry}>
          {t.common.retry}
        </button>
      )}
    </div>
  );
}

/** Фотографий врачей в CRM нет — круг с инициалами, оттенок зависит от имени. */
export function DoctorAvatar({ name, size = 56 }: { name: string; size?: number }) {
  const hue = [...name].reduce((sum, ch) => sum + ch.charCodeAt(0), 0) % 4;
  return (
    <span className={`avatar avatar--${hue}`} style={{ width: size, height: size, fontSize: size * 0.36 }}>
      {initials(name)}
    </span>
  );
}
