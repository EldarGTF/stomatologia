import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { api, type BookingInfo } from '../api';
import { ErrorNote, Loading } from '../components/States';
import { useAsync, useClinic, useTitle } from '../hooks';
import { fill, useT } from '../i18n';
import { brandName, dayMonth, money, parseDateTime, time, weekdayFull } from '../lib/format';

export function BookingDone() {
  const t = useT();
  const clinic = useClinic();
  useTitle(t.done.title, clinic ? brandName(clinic.name) : undefined);
  const { token = '' } = useParams();
  const justBooked = (useLocation().state as { justBooked?: boolean } | null)?.justBooked === true;

  const loaded = useAsync(() => api.booking(token), [token]);
  const [updated, setUpdated] = useState<BookingInfo | null>(null);
  const [asking, setAsking] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const booking = updated ?? loaded.data;

  const cancel = async () => {
    setCancelling(true);
    setCancelError(null);
    try {
      setUpdated(await api.cancel(token));
      setAsking(false);
    } catch (e) {
      setCancelError((e as Error).message);
    } finally {
      setCancelling(false);
    }
  };

  if (loaded.loading && !booking) return <div className="container page-body"><Loading /></div>;
  if (!booking) {
    return (
      <div className="container page-body narrow">
        <ErrorNote message={loaded.error ?? t.done.notFound} />
        <Link to="/booking" className="btn btn--primary">
          {t.nav.book}
        </Link>
      </div>
    );
  }

  const start = parseDateTime(booking.startAt);
  const deadline = parseDateTime(booking.cancelDeadline);
  const cancelled = booking.status === 'CANCELLED';
  const scheduled = booking.status === 'SCHEDULED';
  const phoneHref = clinic ? 'tel:' + clinic.phone.replace(/[^\d+]/g, '') : undefined;

  return (
    <div className="container page-body narrow">
      {justBooked && !cancelled && (
        <div className="done-hero reveal">
          <span className="done-hero__check" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M5 12.5l4.5 4.5L19 7.5" />
            </svg>
          </span>
          <h1 className="page-title">{t.done.justBooked}</h1>
        </div>
      )}
      {!justBooked && <h1 className="page-title">{t.done.title}</h1>}

      <article className={'ticket ticket--full reveal reveal--2' + (cancelled ? ' is-cancelled' : '')}>
        <div className="ticket__head">
          <span className="ticket__caption">{brandName(clinic?.name ?? '')}</span>
          <span className={`status status--${booking.status.toLowerCase()}`}>{t.done.status[booking.status]}</span>
        </div>
        <div className="ticket__body">
          <div className="ticket__when">
            {dayMonth(start)}, {time(booking.startAt)}
          </div>
          <div className="ticket__who">
            {weekdayFull(start)} · до {time(booking.endAt)}
          </div>
        </div>
        <div className="ticket__perforation" aria-hidden="true" />
        <dl className="ticket__details">
          <div>
            <dt>{t.done.service}</dt>
            <dd>
              {booking.serviceName}
              <span className="muted"> · {money(booking.price)}</span>
            </dd>
          </div>
          <div>
            <dt>{t.done.doctor}</dt>
            <dd>
              {booking.doctorName}
              <span className="muted"> · {booking.specialtyName}</span>
            </dd>
          </div>
          <div>
            <dt>{t.done.where}</dt>
            <dd>
              {t.common.room} {booking.roomNumber}
              {clinic && <span className="muted"> · {clinic.address}</span>}
            </dd>
          </div>
          <div>
            <dt>{t.done.patient}</dt>
            <dd>{booking.patientName}</dd>
          </div>
        </dl>
      </article>

      {cancelled ? (
        <div className="note">
          <span>{t.done.cancelled}</span>
          <Link to="/booking" className="btn btn--primary btn--sm">
            {t.done.bookAgain}
          </Link>
        </div>
      ) : (
        scheduled && (
          <>
            <p className={'note ' + (booking.confirmed ? 'note--ok' : 'note--info')}>
              {booking.confirmed ? t.done.confirmed : t.done.unconfirmed}
            </p>
            <p className="muted">{t.done.saveLink}</p>

            <div className="done-actions">
              <a className="btn btn--primary" href={api.ticketUrl(token)} download>
                {t.done.ticket}
              </a>
              {booking.cancellable && !asking && (
                <button className="btn btn--ghost btn--danger" onClick={() => setAsking(true)}>
                  {t.done.cancel}
                </button>
              )}
            </div>

            {asking && (
              <div className="confirm" role="alertdialog" aria-label={t.done.cancel}>
                <span>{t.done.cancelConfirm}</span>
                <div className="confirm__actions">
                  <button className="btn btn--danger-solid btn--sm" onClick={cancel} disabled={cancelling}>
                    {t.done.cancelYes}
                  </button>
                  <button className="btn btn--ghost btn--sm" onClick={() => setAsking(false)} disabled={cancelling}>
                    {t.done.cancelNo}
                  </button>
                </div>
              </div>
            )}
            {cancelError && <ErrorNote message={cancelError} />}

            <p className="muted small">
              {booking.cancellable ? (
                fill(t.done.cancelUntil, { deadline: `${dayMonth(deadline)}, ${time(booking.cancelDeadline)}` })
              ) : (
                <>
                  {t.done.cancelLate} <a href={phoneHref}>{clinic?.phone}</a>
                </>
              )}
            </p>
          </>
        )
      )}
    </div>
  );
}
