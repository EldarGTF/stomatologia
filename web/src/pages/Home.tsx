import { Link } from 'react-router';
import { api, type ServiceInfo, type Slot } from '../api';
import { DoctorAvatar } from '../components/States';
import { useAsync, useClinic, useTitle } from '../hooks';
import { fill, useT } from '../i18n';
import { brandName, money, shortName, slotLabel } from '../lib/format';

/** Для карточки «ближайшее время» берётся консультация — с неё обычно начинают. */
function pickConsultation(services: ServiceInfo[]): ServiceInfo | undefined {
  return services.find((s) => /консультац/i.test(s.name)) ?? services[0];
}

export function Home() {
  const t = useT();
  const clinic = useClinic();
  useTitle(t.home.eyebrow, clinic ? brandName(clinic.name) : undefined);
  const services = useAsync(api.services, []);
  const doctors = useAsync(api.doctors, []);
  const consultation = services.data ? pickConsultation(services.data) : undefined;
  const nearest = useAsync<Slot | null>(
    () => (consultation ? api.nearest(consultation.id, null).catch(() => null) : Promise.resolve(null)),
    [consultation?.id],
  );

  const phoneHref = clinic ? 'tel:' + clinic.phone.replace(/[^\d+]/g, '') : undefined;
  const popular = (services.data ?? []).slice().sort((a, b) => a.price - b.price).slice(0, 6);

  return (
    <>
      <section className="hero">
        <div className="hero__bg" aria-hidden="true">
          <svg viewBox="0 0 800 400" preserveAspectRatio="none">
            <path className="hero__smile" d="M40 120 C 200 380, 600 380, 760 120" />
          </svg>
        </div>
        <div className="container hero__inner">
          <div className="hero__copy reveal">
            <span className="eyebrow">{t.home.eyebrow}</span>
            <h1 className="hero__title">{t.home.title}</h1>
            <p className="hero__lead">{t.home.lead}</p>
            <div className="hero__actions">
              <Link to="/booking" className="btn btn--primary btn--lg">
                {t.home.cta}
              </Link>
              <Link to="/services" className="btn btn--ghost btn--lg">
                {t.home.ctaSecondary}
              </Link>
            </div>
          </div>

          <aside className="ticket reveal reveal--2" aria-live="polite">
            <div className="ticket__head">
              <span className="ticket__caption">{t.home.nearestCaption}</span>
              <span className="ticket__pulse" aria-hidden="true" />
            </div>
            {nearest.loading || services.loading ? (
              <div className="ticket__body ticket__body--skeleton">
                <span className="skeleton skeleton--lg" />
                <span className="skeleton" />
              </div>
            ) : nearest.data && consultation ? (
              <div className="ticket__body">
                <div className="ticket__when">{slotLabel(nearest.data.start)}</div>
                <div className="ticket__who">
                  {shortName(nearest.data.doctorName)} · {t.common.room} {nearest.data.roomNumber}
                </div>
                <div className="ticket__what">
                  {t.home.nearestFor}, {money(consultation.price)}
                </div>
              </div>
            ) : (
              <div className="ticket__body">
                <div className="ticket__who">{t.home.nearestNone}</div>
              </div>
            )}
            <div className="ticket__perforation" aria-hidden="true" />
            {nearest.data && consultation ? (
              <Link
                className="btn btn--primary ticket__cta"
                to={`/booking?service=${consultation.id}&doctor=${nearest.data.doctorId}&slot=${nearest.data.start}`}
              >
                {t.home.nearestBook}
              </Link>
            ) : (
              <a className="btn btn--ghost ticket__cta" href={phoneHref}>
                {clinic?.phone ?? t.home.callUs}
              </a>
            )}
          </aside>
        </div>
      </section>

      <section className="container facts">
        {t.home.facts.map((f, i) => (
          <div className={`fact reveal reveal--${i + 2}`} key={f.value}>
            <span className={`fact__icon fact__icon--${f.value}`} aria-hidden="true" />
            <div>
              <div className="fact__title">{fill(f.title, { days: clinic?.bookingHorizonDays ?? 60 })}</div>
              <div className="fact__text">{f.text}</div>
            </div>
          </div>
        ))}
      </section>

      <section className="container section">
        <h2 className="section__title">{t.home.stepsTitle}</h2>
        <ol className="steps">
          {t.home.steps.map((s, i) => (
            <li className="steps__item" key={s.title}>
              <span className="steps__num">{i + 1}</span>
              <div className="steps__title">{s.title}</div>
              <div className="steps__text">{s.text}</div>
            </li>
          ))}
        </ol>
      </section>

      <section className="container section">
        <div className="section__head">
          <h2 className="section__title">{t.home.servicesTitle}</h2>
          <Link to="/services" className="link-arrow">
            {t.home.allServices}
          </Link>
        </div>
        <div className="grid grid--3">
          {popular.map((s) => (
            <Link key={s.id} to={`/booking?service=${s.id}`} className="card card--service">
              <span className="card__title">{s.name}</span>
              <span className="card__meta">
                {s.durationMinutes} {t.common.minutes}
              </span>
              <span className="card__price">{money(s.price)}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="container section">
        <div className="section__head">
          <h2 className="section__title">{t.home.doctorsTitle}</h2>
          <Link to="/doctors" className="link-arrow">
            {t.home.allDoctors}
          </Link>
        </div>
        <div className="grid grid--4">
          {(doctors.data ?? []).map((d) => (
            <Link key={d.id} to={`/booking?doctor=${d.id}`} className="card card--doctor">
              <DoctorAvatar name={d.fullName} />
              <span className="card__title">{d.fullName}</span>
              <span className="card__meta">{d.specialtyName}</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="section contacts" id="contacts">
        <div className="container contacts__inner">
          <div>
            <h2 className="section__title section__title--light">{t.home.contactsTitle}</h2>
            <p className="contacts__address">{clinic?.address}</p>
          </div>
          <div className="contacts__actions">
            {clinic && (
              <a className="contacts__phone" href={phoneHref}>
                {clinic.phone}
              </a>
            )}
            {clinic?.email && (
              <a className="contacts__mail" href={'mailto:' + clinic.email}>
                {clinic.email}
              </a>
            )}
            <Link to="/booking" className="btn btn--light btn--lg">
              {t.nav.book}
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
