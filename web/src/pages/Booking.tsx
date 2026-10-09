import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { api, ApiError, type DoctorInfo, type ServiceInfo, type Slot } from '../api';
import { DoctorAvatar, ErrorNote, Loading } from '../components/States';
import { useAsync, useClinic, useTitle, type AsyncState } from '../hooks';
import { useT } from '../i18n';
import {
  addDays, brandName, dayMonth, dayPart, dayRange, formatPhone, isoDate, isPhoneComplete, money, parseDate,
  parseDateTime, plural, relativeDay, shortName, time, uniqueByStart, weekdayFull, weekdayShort, isWeekend,
  type DayPart,
} from '../lib/format';

const STRIP_DAYS = 7;
const PARTS: DayPart[] = ['morning', 'day', 'evening'];

/** undefined — врач ещё не выбран, null — «любой свободный врач». */
type DoctorChoice = number | null | undefined;

export function Booking() {
  const t = useT();
  const clinic = useClinic();
  useTitle(t.booking.title, clinic ? brandName(clinic.name) : undefined);
  const [params] = useSearchParams();
  const navigate = useNavigate();

  const services = useAsync(api.services, []);
  const doctors = useAsync(api.doctors, []);

  const initialService = Number(params.get('service')) || undefined;
  const initialDoctor: DoctorChoice = params.get('doctor') ? Number(params.get('doctor')) : undefined;
  const initialSlot = params.get('slot') ?? undefined;

  const [serviceId, setServiceId] = useState<number | undefined>(initialService);
  const [doctorId, setDoctorId] = useState<DoctorChoice>(initialDoctor);
  const [step, setStep] = useState(initialService ? (initialDoctor !== undefined ? 2 : 1) : 0);
  const [date, setDate] = useState<string | undefined>(initialSlot?.slice(0, 10));
  const [slot, setSlot] = useState<Slot | null>(null);
  const [wantedSlot, setWantedSlot] = useState<string | undefined>(initialSlot);

  const service = services.data?.find((s) => s.id === serviceId);
  const doctor = doctors.data?.find((d) => d.id === doctorId);

  const chooseService = (s: ServiceInfo) => {
    setServiceId(s.id);
    setSlot(null);
    setStep(doctorId !== undefined ? 2 : 1);
  };
  const chooseDoctor = (id: number | null) => {
    setDoctorId(id);
    setSlot(null);
    setStep(2);
  };
  const chooseSlot = (s: Slot) => {
    setSlot(s);
    setStep(3);
  };

  const ready = [serviceId !== undefined, doctorId !== undefined, slot !== null];
  const canOpen = (i: number) => i === 0 || ready.slice(0, i).every(Boolean);

  return (
    <div className="container page-body booking">
      <header className="page-head">
        <h1 className="page-title">{t.booking.title}</h1>
        <ol className="stepper" aria-label="Шаги записи">
          {t.booking.steps.map((title, i) => (
            <li key={title}>
              <button
                className={
                  'stepper__item' + (i === step ? ' is-current' : '') + (i < step && canOpen(i) ? ' is-done' : '')
                }
                disabled={!canOpen(i)}
                onClick={() => setStep(i)}
                aria-current={i === step ? 'step' : undefined}
                aria-label={`${i + 1}. ${title}`}
              >
                <span className="stepper__num">{i + 1}</span>
                <span className="stepper__title">{title}</span>
              </button>
            </li>
          ))}
        </ol>
      </header>

      <div className="booking__layout">
        <section className="booking__main" key={step}>
          {step === 0 && (
            <StepService
              state={services}
              selected={serviceId}
              onSelect={chooseService}
            />
          )}
          {step === 1 && (
            <StepDoctor state={doctors} selected={doctorId} onSelect={chooseDoctor} />
          )}
          {step === 2 && service && doctorId !== undefined && (
            <StepTime
              service={service}
              doctorId={doctorId}
              date={date}
              onDate={setDate}
              selected={slot}
              wanted={wantedSlot}
              onWantedHandled={() => setWantedSlot(undefined)}
              onSelect={chooseSlot}
              lastBookableDay={clinic?.lastBookableDay}
            />
          )}
          {step === 3 && service && slot && (
            <StepContacts
              service={service}
              doctorId={doctorId ?? null}
              slot={slot}
              onSlotTaken={() => {
                setSlot(null);
                setStep(2);
              }}
              onBooked={(token) => navigate(`/booking/${token}`, { state: { justBooked: true } })}
            />
          )}
        </section>

        <aside className="summary" aria-label={t.booking.summaryTitle}>
          <div className="summary__caption">{t.booking.summaryTitle}</div>
          {!service ? (
            <p className="summary__empty">{t.booking.summaryEmpty}</p>
          ) : (
            <dl className="summary__list">
              <div>
                <dt>{t.booking.steps[0]}</dt>
                <dd>{service.name}</dd>
              </div>
              <div>
                <dt>{t.booking.steps[1]}</dt>
                <dd>
                  {slot ? shortName(slot.doctorName) : doctor ? shortName(doctor.fullName) : doctorId === null
                    ? t.common.anyDoctor : '—'}
                </dd>
              </div>
              <div>
                <dt>{t.booking.steps[2]}</dt>
                <dd>
                  {slot
                    ? `${relativeDay(parseDateTime(slot.start))}, ${time(slot.start)}–${time(slot.end)}`
                    : '—'}
                </dd>
              </div>
              {slot && (
                <div>
                  <dt>{t.done.where}</dt>
                  <dd>
                    {t.common.room} {slot.roomNumber}
                    {clinic && <span className="summary__address">{clinic.address}</span>}
                  </dd>
                </div>
              )}
            </dl>
          )}
          {service && (
            <div className="summary__total">
              <span>
                {t.booking.price}
                <small>
                  {service.durationMinutes} {t.common.minutes}
                </small>
              </span>
              <strong>{money(service.price)}</strong>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

function StepService({
  state, selected, onSelect,
}: {
  state: AsyncState<ServiceInfo[]>;
  selected: number | undefined;
  onSelect: (s: ServiceInfo) => void;
}) {
  const t = useT();
  return (
    <>
      <h2 className="step__title">{t.booking.serviceTitle}</h2>
      <p className="step__hint">{t.booking.serviceHint}</p>
      {state.loading && <Loading />}
      {state.error && <ErrorNote message={state.error} onRetry={state.reload} />}
      <div className="choice-list">
        {(state.data ?? []).map((s) => (
          <button
            key={s.id}
            className={'choice' + (s.id === selected ? ' is-selected' : '')}
            onClick={() => onSelect(s)}
          >
            <span className="choice__main">
              <span className="choice__title">{s.name}</span>
              <span className="choice__meta">
                {s.durationMinutes} {t.common.minutes}
              </span>
            </span>
            <span className="choice__price">{money(s.price)}</span>
          </button>
        ))}
      </div>
    </>
  );
}

function StepDoctor({
  state, selected, onSelect,
}: {
  state: AsyncState<DoctorInfo[]>;
  selected: DoctorChoice;
  onSelect: (id: number | null) => void;
}) {
  const t = useT();
  return (
    <>
      <h2 className="step__title">{t.booking.doctorTitle}</h2>
      {state.loading && <Loading />}
      {state.error && <ErrorNote message={state.error} onRetry={state.reload} />}
      <div className="choice-list">
        <button className={'choice choice--any' + (selected === null ? ' is-selected' : '')} onClick={() => onSelect(null)}>
          <span className="avatar avatar--any" aria-hidden="true">
            ✦
          </span>
          <span className="choice__main">
            <span className="choice__title">{t.common.anyDoctor}</span>
            <span className="choice__meta">{t.booking.anyDoctorHint}</span>
          </span>
        </button>
        {(state.data ?? []).map((d) => (
          <button
            key={d.id}
            className={'choice' + (d.id === selected ? ' is-selected' : '')}
            onClick={() => onSelect(d.id)}
          >
            <DoctorAvatar name={d.fullName} size={44} />
            <span className="choice__main">
              <span className="choice__title">{d.fullName}</span>
              <span className="choice__meta">
                {d.specialtyName} · {t.common.room} {d.roomNumber}
              </span>
            </span>
          </button>
        ))}
      </div>
    </>
  );
}

function StepTime({
  service, doctorId, date, onDate, selected, wanted, onWantedHandled, onSelect, lastBookableDay,
}: {
  service: ServiceInfo;
  doctorId: number | null;
  date: string | undefined;
  onDate: (d: string) => void;
  selected: Slot | null;
  wanted: string | undefined;
  onWantedHandled: () => void;
  onSelect: (s: Slot) => void;
  lastBookableDay: string | undefined;
}) {
  const t = useT();
  const today = useMemo(() => parseDate(isoDate(new Date())), []);
  const lastDay = lastBookableDay ? parseDate(lastBookableDay) : addDays(today, 60);
  const [stripStart, setStripStart] = useState<Date>(() => {
    const d = date ? parseDate(date) : today;
    return d < today ? today : d > addDays(today, STRIP_DAYS - 1) ? d : today;
  });
  const [nearestError, setNearestError] = useState<string | null>(null);
  const [findingNearest, setFindingNearest] = useState(false);

  const days = dayRange(stripStart, STRIP_DAYS).filter((d) => d <= lastDay);
  const from = isoDate(stripStart);
  const to = isoDate(days[days.length - 1] ?? stripStart);

  const availability = useAsync(() => api.days(service.id, doctorId, from, to), [service.id, doctorId, from, to]);
  const holidays = useAsync(() => api.holidays(from, to), [from, to]);
  const free = new Map((availability.data ?? []).map((d) => [d.date, d.freeSlots]));
  const holidayName = new Map((holidays.data ?? []).map((h) => [h.day, h.name]));

  // Если день не выбран или в нём нет окон — встаём на первый свободный день ленты.
  useEffect(() => {
    if (!availability.data) return;
    if (date && (free.has(date) || date < from || date > to)) return;
    const first = availability.data[0]?.date;
    if (first) onDate(first);
  }, [availability.data]);

  const slots = useAsync<Slot[]>(
    () => (date ? api.slots(service.id, doctorId, date) : Promise.resolve([])),
    [service.id, doctorId, date],
  );
  const visible = doctorId === null ? uniqueByStart(slots.data ?? []) : slots.data ?? [];

  // Время из ссылки «Занять это время» с главной выбирается автоматически, если оно ещё свободно.
  const handledWanted = useRef(false);
  useEffect(() => {
    if (!wanted || handledWanted.current || !slots.data || date !== wanted.slice(0, 10)) return;
    handledWanted.current = true;
    onWantedHandled();
    const match = slots.data.find((s) => s.start.slice(0, 16) === wanted.slice(0, 16));
    if (match) onSelect(match);
  }, [slots.data]);

  const findNearest = async () => {
    setFindingNearest(true);
    setNearestError(null);
    try {
      const s = await api.nearest(service.id, doctorId);
      const d = parseDate(s.start);
      if (d < stripStart || d > addDays(stripStart, STRIP_DAYS - 1)) setStripStart(d);
      onDate(s.start.slice(0, 10));
      onSelect(s);
    } catch (e) {
      setNearestError((e as Error).message);
    } finally {
      setFindingNearest(false);
    }
  };

  const canPrev = stripStart > today;
  const canNext = addDays(stripStart, STRIP_DAYS) <= lastDay;
  const dateObj = date ? parseDate(date) : undefined;

  return (
    <>
      <div className="step__row">
        <h2 className="step__title">{t.booking.timeTitle}</h2>
        <button className="btn btn--accent btn--sm" onClick={findNearest} disabled={findingNearest}>
          ⚡ {t.booking.nearest}
        </button>
      </div>
      {nearestError && <ErrorNote message={nearestError} />}

      <div className="strip">
        <button
          className="strip__arrow"
          disabled={!canPrev}
          onClick={() => setStripStart((d) => (addDays(d, -STRIP_DAYS) < today ? today : addDays(d, -STRIP_DAYS)))}
          aria-label="Предыдущие дни"
        >
          ‹
        </button>
        <div className="strip__days">
          {days.map((d) => {
            const iso = isoDate(d);
            const count = free.get(iso) ?? 0;
            const holiday = holidayName.get(iso);
            const disabled = !count;
            return (
              <button
                key={iso}
                className={
                  'day' + (iso === date ? ' is-selected' : '') + (isWeekend(d) ? ' is-weekend' : '') +
                  (holiday ? ' is-holiday' : '')
                }
                disabled={disabled || availability.loading}
                onClick={() => onDate(iso)}
                title={holiday ? `${t.booking.holiday}: ${holiday}` : undefined}
              >
                <span className="day__week">{weekdayShort(d)}</span>
                <span className="day__num">{d.getDate()}</span>
                <span className="day__free">
                  {availability.loading ? '·' : count || '—'}
                </span>
              </button>
            );
          })}
        </div>
        <button
          className="strip__arrow"
          disabled={!canNext}
          onClick={() => setStripStart((d) => addDays(d, STRIP_DAYS))}
          aria-label="Следующие дни"
        >
          ›
        </button>
      </div>

      {availability.error && <ErrorNote message={availability.error} onRetry={availability.reload} />}
      {availability.data && availability.data.length === 0 && (
        <p className="note">{t.booking.noSlotsRange}</p>
      )}

      {dateObj && (
        <div className="slots">
          <div className="slots__date">
            {weekdayFull(dateObj)}, {dayMonth(dateObj)}
            {visible.length > 0 && (
              <span className="slots__count">
                {visible.length} {plural(visible.length, ...t.booking.freeSlots)}
              </span>
            )}
          </div>
          {slots.loading && <Loading />}
          {slots.error && <ErrorNote message={slots.error} onRetry={slots.reload} />}
          {slots.data && visible.length === 0 && <p className="muted">{t.booking.noSlotsDay}</p>}
          {PARTS.map((part) => {
            const items = visible.filter((s) => dayPart(s.start) === part);
            if (!items.length) return null;
            return (
              <div className="slots__group" key={part}>
                <div className="slots__part">{t.booking.parts[part]}</div>
                <div className="slots__grid">
                  {items.map((s) => (
                    <button
                      key={s.start + s.doctorId}
                      className={'slot' + (selected?.start === s.start && selected.doctorId === s.doctorId
                        ? ' is-selected' : '')}
                      onClick={() => onSelect(s)}
                      title={doctorId === null ? undefined : s.doctorName}
                    >
                      {time(s.start)}
                    </button>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </>
  );
}

function StepContacts({
  service, doctorId, slot, onSlotTaken, onBooked,
}: {
  service: ServiceInfo;
  doctorId: number | null;
  slot: Slot;
  onSlotTaken: () => void;
  onBooked: (token: string) => void;
}) {
  const t = useT();
  const [lastName, setLastName] = useState('');
  const [firstName, setFirstName] = useState('');
  const [phone, setPhone] = useState('');
  const [comment, setComment] = useState('');
  const [consent, setConsent] = useState(false);
  const [website, setWebsite] = useState('');
  const [touched, setTouched] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [taken, setTaken] = useState(false);

  const errors = {
    lastName: !lastName.trim() ? t.booking.required : null,
    firstName: !firstName.trim() ? t.booking.required : null,
    phone: !phone ? t.booking.required : !isPhoneComplete(phone) ? t.booking.phoneIncomplete : null,
    consent: !consent ? t.booking.consentRequired : null,
  };
  const valid = Object.values(errors).every((e) => !e);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!valid || sending) return;
    setSending(true);
    setError(null);
    try {
      const booking = await api.book({
        serviceId: service.id,
        // «Любой врач»: сервер сам найдёт свободного на это время, если выбранный уже занят
        doctorId: doctorId === null ? null : slot.doctorId,
        startAt: slot.start,
        lastName: lastName.trim(),
        firstName: firstName.trim(),
        phone,
        comment: comment.trim() || null,
        consent,
        website,
      });
      onBooked(booking.token);
    } catch (err) {
      const apiError = err as ApiError;
      setTaken(apiError.status === 409 && /занят|заняли/i.test(apiError.message));
      setError(apiError.message);
      setSending(false);
    }
  };

  const fieldError = (key: keyof typeof errors) =>
    touched && errors[key] ? <span className="field__error">{errors[key]}</span> : null;

  return (
    <form className="form" onSubmit={submit} noValidate>
      <h2 className="step__title">{t.booking.contactsTitle}</h2>
      <div className="form__row">
        <label className="field">
          <span className="field__label">{t.booking.lastName}</span>
          <input
            className="input"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            autoComplete="family-name"
            maxLength={60}
            aria-invalid={touched && !!errors.lastName}
          />
          {fieldError('lastName')}
        </label>
        <label className="field">
          <span className="field__label">{t.booking.firstName}</span>
          <input
            className="input"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            autoComplete="given-name"
            maxLength={60}
            aria-invalid={touched && !!errors.firstName}
          />
          {fieldError('firstName')}
        </label>
      </div>
      <label className="field">
        <span className="field__label">{t.booking.phone}</span>
        <input
          className="input"
          type="tel"
          inputMode="tel"
          placeholder="+7 (7__) ___-__-__"
          value={phone}
          onChange={(e) => setPhone(formatPhone(e.target.value))}
          autoComplete="tel"
          aria-invalid={touched && !!errors.phone}
        />
        {fieldError('phone') ?? <span className="field__hint">{t.booking.phoneHint}</span>}
      </label>
      <label className="field">
        <span className="field__label">
          {t.booking.comment} <span className="field__optional">— {t.booking.commentHint}</span>
        </span>
        <textarea
          className="input input--area"
          rows={3}
          maxLength={500}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
      </label>
      {/* Ловушка для ботов: поле скрыто от людей и программ чтения экрана */}
      <input
        className="trap"
        type="text"
        name="website"
        tabIndex={-1}
        autoComplete="off"
        value={website}
        onChange={(e) => setWebsite(e.target.value)}
        aria-hidden="true"
      />
      <label className="check">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>
          {t.booking.consentBefore}
          <Link to="/privacy" target="_blank">
            {t.booking.consentLink}
          </Link>
        </span>
      </label>
      {fieldError('consent')}

      {error && (
        <div className="note note--error" role="alert">
          <span>{error}</span>
          {taken && (
            <button type="button" className="btn btn--ghost btn--sm" onClick={onSlotTaken}>
              {t.booking.steps[2]}
            </button>
          )}
        </div>
      )}

      <button className="btn btn--primary btn--lg form__submit" type="submit" disabled={sending}>
        {sending ? t.booking.submitting : `${t.booking.submit} · ${relativeDay(parseDateTime(slot.start))}, ${time(slot.start)}`}
      </button>
    </form>
  );
}
